package com.rivers.nba.service;

import com.alibaba.fastjson.JSONObject;
import com.rivers.nba.entity.Game;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * 比分实时推送服务（SSE 广播：后台发现比分变动后主动推送前台，前台无需轮询）
 * </p>
 *
 * @author rivers
 * @since 2026-10-08
 */
@Slf4j
@Service
public class ScorePushService {

    /**
     * 比分变动事件名（前台按此事件名监听）
     */
    public static final String EVENT_SCORE_UPDATE = "scoreUpdate";

    /**
     * 比分变动广播频道（多实例部署：任一变更来源发布到该频道，所有实例订阅后推送各自本地 SSE 客户端）
     */
    public static final String BROADCAST_CHANNEL = "nba:score:update";

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 当前在线的比分推送订阅者（每个 SSE 连接一个独立 emitter，断开自动移除，避免共享 Sink 状态残留导致后续订阅收不到事件）
     */
    private final Map<Long, FluxSink<ServerSentEvent<String>>> subscribers = new ConcurrentHashMap<>();

    private final AtomicLong subscriberSeq = new AtomicLong();

    private final ReactiveStringRedisTemplate redisTemplate;

    public ScorePushService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 启动时订阅比分广播频道：收到任意实例发布的比分变动后，推送本实例的 SSE 订阅者
     */
    @PostConstruct
    public void initBroadcastSubscription() {
        redisTemplate.listenToChannel(BROADCAST_CHANNEL)
                .doOnSubscribe(_ -> log.info("比分广播订阅已启动: channel={}", BROADCAST_CHANNEL))
                .doOnNext(message -> pushScore(message.getMessage()))
                .doOnError(e -> log.error("比分广播订阅异常，稍后重试: {}", e.getMessage()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5)))
                .subscribe(_ -> {
                }, e -> log.error("比分广播订阅终止: {}", e.getMessage()));
    }

    /**
     * 前台订阅的比分事件流（每连接独立 emitter + 30 秒心跳保活，防止网关/代理空闲断流）
     */
    public Flux<ServerSentEvent<String>> stream() {
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(_ -> ServerSentEvent.<String>builder().comment("ping").build());
        return Flux.merge(events(), heartbeat);
    }

    /**
     * 构造向当前订阅者广播的事件源（订阅时登记，取消或释放时移除）
     */
    private Flux<ServerSentEvent<String>> events() {
        return Flux.create(emitter -> {
            long subscriberId = subscriberSeq.incrementAndGet();
            subscribers.put(subscriberId, emitter);
            log.info("比分推送订阅者加入: id={}, 在线数={}", subscriberId, subscribers.size());
            emitter.onDispose(() -> {
                subscribers.remove(subscriberId);
                log.info("比分推送订阅者退出: id={}, 在线数={}", subscriberId, subscribers.size());
            });
        }, FluxSink.OverflowStrategy.LATEST);
    }

    /**
     * 发布比分变动到广播频道（多实例部署：所有实例订阅后各自推送本地 SSE 客户端，保证每个客户端恰好收到一份）
     */
    public void broadcastScoreChange(Game game) {
        String data = buildScoreJson(game);
        redisTemplate.convertAndSend(BROADCAST_CHANNEL, data)
                .doOnNext(receivers -> log.info("比分变动已发布到广播频道: 频道订阅数={}, data={}", receivers, data))
                .doOnError(e -> log.error("比分变动发布失败: gameId={}, err={}", game.getGameId(), e.getMessage()))
                .subscribe();
    }

    /**
     * 推送比分事件给本地 SSE 订阅者（无订阅者时静默丢弃，慢订阅者按最新值覆盖）
     */
    private void pushScore(String data) {
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .event(EVENT_SCORE_UPDATE)
                .data(data)
                .build();
        int pushed = 0;
        for (Map.Entry<Long, FluxSink<ServerSentEvent<String>>> entry : subscribers.entrySet()) {
            FluxSink<ServerSentEvent<String>> subscriber = entry.getValue();
            if (subscriber.isCancelled()) {
                subscribers.remove(entry.getKey());
                continue;
            }
            try {
                subscriber.next(event);
                pushed++;
            } catch (Exception e) {
                log.warn("比分事件推送失败, 移除订阅者: id={}", entry.getKey());
                subscribers.remove(entry.getKey());
            }
        }
        log.info("比分事件已广播本地订阅者: 在线订阅={}, data={}", pushed, data);
    }

    /**
     * 构造比分事件 JSON（字段名与列表接口 camelCase 保持一致）
     */
    private String buildScoreJson(Game game) {
        JSONObject json = new JSONObject();
        json.put("gameId", defaultInt(game.getGameId()));
        json.put("day", formatDate(game.getDay()));
        json.put("dateTime", formatDateTime(game.getDateTime()));
        json.put("awayTeam", defaultString(game.getAwayTeam()));
        json.put("homeTeam", defaultString(game.getHomeTeam()));
        json.put("awayTeamScore", defaultInt(game.getAwayTeamScore()));
        json.put("homeTeamScore", defaultInt(game.getHomeTeamScore()));
        json.put("status", defaultString(game.getStatus()));
        json.put("isClosed", Boolean.TRUE.equals(game.getIsClosed()));
        return json.toJSONString();
    }

    /**
     * 日期格式化为 yyyy-MM-dd（空值用默认值替换）
     */
    private static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * 日期时间格式化为 yyyy-MM-dd HH:mm:ss（空值用默认值替换）
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 字符串空值用默认值替换（null 转空串）
     */
    private static String defaultString(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    /**
     * 整数空值用默认值替换（null 转 0）
     */
    private static int defaultInt(Integer value) {
        if (value == null) {
            return 0;
        }
        return value;
    }
}

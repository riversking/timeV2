package com.rivers.nba.job;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.nba.entity.Game;
import com.rivers.nba.repository.GameRepository;
import com.rivers.nba.service.ScorePushService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 比分实时监控检测逻辑：对比数据源与本地库中今日比赛的比分，
 * 发现变动即更新本地并广播 SSE 事件（检测由 timer-batch 统一调度触发，
 * 后台承担检测职责，前台只订阅不轮询）
 * </p>
 *
 * @author rivers
 * @since 2026-10-08
 */
@Slf4j
@Component
public class ScoreMonitorJob {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final GameRepository gameRepository;

    private final ScorePushService scorePushService;

    public ScoreMonitorJob(GameRepository gameRepository, ScorePushService scorePushService) {
        this.gameRepository = gameRepository;
        this.scorePushService = scorePushService;
    }

    /**
     * 执行一轮比分检测（由 timer-batch 的 scoreMonitorTask 任务定时触发，无今日比赛时直接跳过、不发起外部请求）；
     * 分布式锁保留作防御：手动触发或异常重复调用时，同一时刻仍仅一个实例执行检测
     */
    @SchedulerLock(name = "nbaScoreMonitorJob", lockAtMostFor = "2m")
    public void checkScoreChanges() {
        try {
            log.info("比分监控任务开始检测");
            doCheckScoreChanges().block(Duration.ofSeconds(50));
        } catch (Exception e) {
            log.error("比分实时监控异常: {}", e.getMessage());
        }
    }

    /**
     * 执行一轮比分检测（拉取数据源当日比赛，与本地未结束比赛对比，发现变动即更新并广播）
     */
    private Mono<Void> doCheckScoreChanges() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return gameRepository.findActiveByDay(today)
                .collectList()
                .filter(games -> !games.isEmpty())
                .flatMap(games -> syncChangedGames(games, today))
                .then();
    }

    /**
     * 拉取数据源当日比赛，与本地未结束比赛逐一对比，发现变动即更新并广播
     */
    private Mono<Void> syncChangedGames(List<Game> dbGames, String today) {
        return Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "GamesByDate/" + today + "?key=" + nbaKey))
                .flatMapMany(json -> Flux.fromIterable(parseRemoteGames(json)))
                .collectList()
                .map(list -> list.stream()
                        .collect(Collectors.toMap(Game::getGameId, Function.identity(), (first, second) -> first)))
                .flatMapMany(remoteMap -> Flux.fromIterable(dbGames)
                        .filter(db -> isScoreChanged(db, remoteMap.get(db.getGameId())))
                        .concatMap(db -> applyRemoteChange(db, remoteMap.get(db.getGameId()))))
                .then();
    }

    /**
     * 解析数据源比赛列表（解析失败按空列表处理）
     */
    private List<Game> parseRemoteGames(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        return Optional.ofNullable(JSON.parseArray(json, Game.class))
                .map(list -> list.stream()
                        .filter(game -> game.getGameId() != null)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * 判断比分是否有变动（数据源缺失该场时视为无变动）
     */
    private boolean isScoreChanged(Game db, Game remote) {
        if (remote == null) {
            return false;
        }
        if (!Objects.equals(db.getAwayTeamScore(), remote.getAwayTeamScore())) {
            return true;
        }
        if (!Objects.equals(db.getHomeTeamScore(), remote.getHomeTeamScore())) {
            return true;
        }
        if (!Objects.equals(db.getIsClosed(), remote.getIsClosed())) {
            return true;
        }
        return !Objects.equals(db.getStatus(), remote.getStatus());
    }

    /**
     * 应用数据源变动到本地并广播事件
     */
    private Mono<Void> applyRemoteChange(Game db, Game remote) {
        db.setAwayTeamScore(remote.getAwayTeamScore());
        db.setHomeTeamScore(remote.getHomeTeamScore());
        db.setStatus(remote.getStatus());
        db.setIsClosed(remote.getIsClosed());
        if (remote.getUpdated() != null) {
            db.setUpdated(remote.getUpdated());
        }
        db.setUpdateUser("system");
        log.info("发现比分变动: gameId={}, {} {}:{} {}", db.getGameId(), db.getAwayTeam(),
                defaultInt(remote.getAwayTeamScore()), defaultInt(remote.getHomeTeamScore()), db.getHomeTeam());
        return gameRepository.updateScore(defaultInt(db.getGameId()),
                        defaultInt(db.getAwayTeamScore()), defaultInt(db.getHomeTeamScore()),
                        db.getStatus(), Boolean.TRUE.equals(db.getIsClosed()), db.getUpdated(), db.getUpdateUser())
                .doOnNext(_ -> scorePushService.broadcastScoreChange(db))
                .then();
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

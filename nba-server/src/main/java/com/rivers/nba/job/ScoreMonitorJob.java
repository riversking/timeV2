package com.rivers.nba.job;

import com.rivers.nba.client.EspnScoreboardClient;
import com.rivers.nba.client.EspnScoreboardClient.EspnGame;
import com.rivers.nba.entity.Game;
import com.rivers.nba.repository.GameRepository;
import com.rivers.nba.service.ScorePushService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 比分实时监控检测逻辑：对比数据源与本地库中今日比赛的比分，
 * 发现变动即更新本地并广播 SSE 事件（检测由 timer-batch 统一调度触发，
 * 后台承担检测职责，前台只订阅不轮询）。
 * 数据源为 ESPN 公开计分板接口（免密钥、无配额压力；原 SportsData ScoresBasic 按调用计配额，
 * 在每分钟一轮的检测频率下不可持续）；库内 gameId 与 ESPN event.id 不同源，
 * 对阵按「美东比赛日 + 主客队缩写」对齐
 * </p>
 *
 * @author rivers
 * @since 2026-10-08
 */
@Slf4j
@Component
public class ScoreMonitorJob {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ZoneId GAME_ZONE = ZoneId.of("America/New_York");

    private final GameRepository gameRepository;

    private final ScorePushService scorePushService;

    private final EspnScoreboardClient espnScoreboardClient;

    public ScoreMonitorJob(GameRepository gameRepository, ScorePushService scorePushService,
                           EspnScoreboardClient espnScoreboardClient) {
        this.gameRepository = gameRepository;
        this.scorePushService = scorePushService;
        this.espnScoreboardClient = espnScoreboardClient;
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
     * 执行一轮比分检测（拉取数据源比赛日的比赛，与本地未结束比赛对比，发现变动即更新并广播）；
     * 比赛日按美国东部日期计算——NBA 比赛在北京时间美东当日夜间至次日中午进行，
     * 北京时间上午（比赛进行中）服务器本地日期已比美东比赛日大 1 天，若按本地日期拉取会拿到尚未开赛的比赛；
     * 同时兼查美东昨日：美东午夜后仍在进行的比赛归属前一比赛日
     */
    private Mono<Void> doCheckScoreChanges() {
        LocalDate usToday = LocalDate.now(GAME_ZONE);
        LocalDate usYesterday = usToday.minusDays(1);
        String today = usToday.format(DATE_FORMATTER);
        String yesterday = usYesterday.format(DATE_FORMATTER);
        return Mono.zip(
                        gameRepository.findActiveByDay(today).collectList(),
                        gameRepository.findActiveByDay(yesterday).collectList())
                .filter(tuple -> !tuple.getT1().isEmpty() || !tuple.getT2().isEmpty())
                .flatMap(tuple -> syncChangedGames(tuple.getT1(), tuple.getT2(), today, yesterday))
                .then();
    }

    /**
     * 拉取数据源比赛日的比赛（含美东昨日，并行拉取、单侧失败不阻断另一侧），与本地未结束比赛逐一对比，
     * 发现变动即更新并广播；对阵按「美东比赛日 + 主客队缩写」匹配
     */
    private Mono<Void> syncChangedGames(List<Game> todayGames, List<Game> yesterdayGames, String today, String yesterday) {
        Mono<List<EspnGame>> todayRemote = espnScoreboardClient.fetchGames(today);
        Mono<List<EspnGame>> yesterdayRemote = yesterdayGames.isEmpty()
                ? Mono.just(List.of())
                : espnScoreboardClient.fetchGames(yesterday);
        return Mono.zip(todayRemote, yesterdayRemote)
                .flatMapMany(tuple -> {
                    Map<String, EspnGame> remoteMap = new LinkedHashMap<>();
                    for (EspnGame game : tuple.getT2()) {
                        remoteMap.put(game.matchKey(), game);
                    }
                    for (EspnGame game : tuple.getT1()) {
                        remoteMap.put(game.matchKey(), game);
                    }
                    return Flux.concat(Flux.fromIterable(yesterdayGames), Flux.fromIterable(todayGames))
                            .filter(db -> isScoreChanged(db, remoteMap.get(matchKey(db))))
                            .concatMap(db -> applyRemoteChange(db, remoteMap.get(matchKey(db))));
                })
                .then();
    }

    /**
     * 本地比赛的匹配键（美东比赛日 + 主客队缩写，与 ESPN 侧口径一致）
     */
    private static String matchKey(Game db) {
        return EspnScoreboardClient.matchKey(db.getDay().toLocalDate(), db.getAwayTeam(), db.getHomeTeam());
    }

    /**
     * 判断比分是否有变动（数据源缺失该场时视为无变动）
     */
    private boolean isScoreChanged(Game db, EspnGame remote) {
        if (remote == null) {
            return false;
        }
        if (!Objects.equals(db.getAwayTeamScore(), remote.awayScore())) {
            return true;
        }
        if (!Objects.equals(db.getHomeTeamScore(), remote.homeScore())) {
            return true;
        }
        if (Boolean.TRUE.equals(db.getIsClosed()) != remote.closed()) {
            return true;
        }
        return !Objects.equals(db.getStatus(), remote.status());
    }

    /**
     * 应用数据源变动到本地并广播事件；比分沿用数据源原始值（未开赛为 null，写库同样保留 NULL，
     * 不得用 defaultInt 转 0，否则下一轮检测会因 0 与数据源 null 不一致而反复纠错）
     */
    private Mono<Void> applyRemoteChange(Game db, EspnGame remote) {
        log.info("发现比分变动: gameId={}, {} {}:{} {} status:{}=>{}, closed:{}=>{}", db.getGameId(), db.getAwayTeam(),
                defaultInt(remote.awayScore()), defaultInt(remote.homeScore()), db.getHomeTeam(),
                db.getStatus(), remote.status(), db.getIsClosed(), remote.closed());
        db.setAwayTeamScore(remote.awayScore());
        db.setHomeTeamScore(remote.homeScore());
        db.setStatus(remote.status());
        db.setIsClosed(remote.closed());
        db.setUpdated(LocalDateTime.now(GAME_ZONE));
        db.setUpdateUser("system");
        return gameRepository.updateScore(defaultInt(db.getGameId()),
                        db.getAwayTeamScore(), db.getHomeTeamScore(),
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

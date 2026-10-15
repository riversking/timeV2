package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.service.IGameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 同步比赛任务处理器：接入 timer-batch 统一调度（Quartz 集群互斥保证多实例下同一时刻仅一次触发），
 * 触发时同步美东当日比赛数据——北京时间 01:30 触发时美东为前一日中午，拉取的正是当日夜间
 * （北京次日凌晨）开赛的赛程，提前拉入本地作为门户赛程展示与比分监控比对的底数
 * </p>
 *
 * @author rivers
 * @since 2026-10-10
 */
@Component("syncGamesTask")
@Slf4j
public class SyncGamesTaskHandler extends BusinessTaskHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ZoneId GAME_ZONE = ZoneId.of("America/New_York");

    private final IGameService gameService;

    public SyncGamesTaskHandler(IGameService gameService) {
        this.gameService = gameService;
    }

    /**
     * 同步当日比赛（timer-batch 按 taskName=syncGamesTask 分发到本方法）
     */
    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        // 比赛日按美国东部日期计算：NBA 比赛北京时间均在美东次日凌晨至中午开赛
        String gameDay = LocalDate.now(GAME_ZONE).format(DATE_FORMATTER);
        log.info("开始同步比赛信息, 比赛日(美东):{}", gameDay);
        SyncGamesByDateReq req = SyncGamesByDateReq.newBuilder().setDate(gameDay).build();
        gameService.syncGamesByDate(req).block(Duration.ofMinutes(2));
        log.info("同步比赛信息结束, 比赛日(美东):{}", gameDay);
    }
}

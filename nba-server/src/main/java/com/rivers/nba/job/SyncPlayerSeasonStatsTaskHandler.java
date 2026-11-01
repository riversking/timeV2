package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import com.rivers.nba.service.ILeagueCompareService;
import com.rivers.nba.service.IPlayerSeasonHistoryService;
import com.rivers.nba.service.IRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * <p>
 * 球员当季赛季数据同步任务：当季整季刷新（排行榜 + 历年数据的常规赛/季后赛/季前赛），
 * 并补齐联盟对比；历史赛季数据由 initPlayerHistoryTask 初始化一次
 * （建表为手动执行 sql/nba_player_stat_tables.sql，服务不做自动 DDL）
 * </p>
 *
 * @author rivers
 * @since 2026-10-28
 */
@Component("syncPlayerSeasonStatsTask")
@Slf4j
public class SyncPlayerSeasonStatsTaskHandler extends BusinessTaskHandler {

    private final IRankingService rankingService;

    private final IPlayerSeasonHistoryService playerSeasonHistoryService;

    private final ILeagueCompareService leagueCompareService;

    public SyncPlayerSeasonStatsTaskHandler(IRankingService rankingService,
                                            IPlayerSeasonHistoryService playerSeasonHistoryService,
                                            ILeagueCompareService leagueCompareService) {
        this.rankingService = rankingService;
        this.playerSeasonHistoryService = playerSeasonHistoryService;
        this.leagueCompareService = leagueCompareService;
    }

    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        log.info("开始同步球员当季数据");
        rankingService.syncPlayerSeasonStats().block(Duration.ofMinutes(10));
        playerSeasonHistoryService.syncRecent().block(Duration.ofMinutes(30));
        leagueCompareService.backfillMissing().block(Duration.ofMinutes(10));
        log.info("同步球员当季数据结束");
    }
}

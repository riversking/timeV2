package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import com.rivers.nba.service.IPlayerSeasonHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * <p>
 * 球员历史数据初始化任务：历史赛季常规赛数据齐全时直接跳过，
 * 仅在缺失（首次部署或上次回填未完成）时执行一次全量回填；
 * 当季数据由 syncPlayerSeasonStatsTask 每日刷新
 * </p>
 *
 * @author rivers
 * @since 2026-11-02
 */
@Component("initPlayerHistoryTask")
@Slf4j
public class InitPlayerHistoryTaskHandler extends BusinessTaskHandler {

    private final IPlayerSeasonHistoryService playerSeasonHistoryService;

    public InitPlayerHistoryTaskHandler(IPlayerSeasonHistoryService playerSeasonHistoryService) {
        this.playerSeasonHistoryService = playerSeasonHistoryService;
    }

    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        Boolean initialized = playerSeasonHistoryService.isHistoryInitialized().block(Duration.ofMinutes(2));
        if (Boolean.TRUE.equals(initialized)) {
            log.info("球员历史数据已初始化，跳过");
            return;
        }
        log.info("开始初始化球员历史数据");
        playerSeasonHistoryService.backfillMissing().block(Duration.ofMinutes(60));
        log.info("球员历史数据初始化结束");
    }
}

package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import com.rivers.nba.service.IRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * <p>
 * 同步排名数据任务处理器：接入 timer-batch 统一调度（每天两次），
 * 触发时拉取球队排名与球员赛季统计全量落库，门户排行接口随后直接读库
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
@Component("syncRankingsTask")
@Slf4j
public class SyncRankingsTaskHandler extends BusinessTaskHandler {

    private final IRankingService rankingService;

    public SyncRankingsTaskHandler(IRankingService rankingService) {
        this.rankingService = rankingService;
    }

    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        log.info("开始同步排名数据");
        rankingService.syncRankings().block(Duration.ofMinutes(2));
        log.info("同步排名数据结束");
    }
}

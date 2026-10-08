package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 比分监控任务处理器：接入 timer-batch 统一调度（Quartz 集群互斥保证多实例下同一时刻仅一次触发），
 * 触发时执行一轮比分检测（检测逻辑复用 {@link ScoreMonitorJob}）
 * </p>
 *
 * @author rivers
 * @since 2026-10-09
 */
@Component("scoreMonitorTask")
@Slf4j
public class ScoreMonitorTaskHandler extends BusinessTaskHandler {

    private final ScoreMonitorJob scoreMonitorJob;

    public ScoreMonitorTaskHandler(ScoreMonitorJob scoreMonitorJob) {
        this.scoreMonitorJob = scoreMonitorJob;
    }

    /**
     * 执行比分检测（timer-batch 按 taskName=scoreMonitorTask 分发到本方法）
     */
    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        log.info("timer-batch 触发比分监控任务");
        scoreMonitorJob.checkScoreChanges();
    }
}

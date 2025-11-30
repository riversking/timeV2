package com.rivers.nba.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import com.rivers.nba.service.IPlayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("syncPlayerJob")
@Slf4j
public class SyncPlayerJob extends BusinessTaskHandler {

    private final IPlayerService playerService;

    public SyncPlayerJob(IPlayerService playerService) {
        this.playerService = playerService;
    }

    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        log.info("开始同步球员信息");
        playerService.syncAllPlayer();
        log.info("同步球员信息结束");
    }
}

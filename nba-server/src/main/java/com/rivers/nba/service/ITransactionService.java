package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTransactionsReq;
import com.rivers.nba.GetTransactionsRes;
import com.rivers.nba.SyncTransactionsByDateReq;
import reactor.core.publisher.Mono;

/**
 * 交易记录表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface ITransactionService {

    /**
     * 按日期同步交易记录
     */
    Mono<ResultVO<Void>> syncTransactionsByDate(SyncTransactionsByDateReq req);

    /**
     * 分页查询交易记录（date 可选过滤）
     */
    Mono<ResultVO<GetTransactionsRes>> getTransactionPage(GetTransactionsReq req);
}

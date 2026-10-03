package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTransactionsReq;
import com.rivers.nba.GetTransactionsRes;
import com.rivers.nba.SyncTransactionsByDateReq;
import com.rivers.nba.service.ITransactionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 交易记录表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final ITransactionService transactionService;

    public TransactionController(ITransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("syncTransactionsByDate")
    public Mono<ResultVO<Void>> syncTransactionsByDate(@RequestBody SyncTransactionsByDateReq req) {
        return transactionService.syncTransactionsByDate(req);
    }

    @PostMapping("getTransactionPage")
    public Mono<ResultVO<GetTransactionsRes>> getTransactionPage(@RequestBody GetTransactionsReq req) {
        return transactionService.getTransactionPage(req);
    }
}

package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTransactionsReq;
import com.rivers.nba.GetTransactionsRes;
import com.rivers.nba.SyncTransactionsByDateReq;
import com.rivers.nba.TransactionInfo;
import com.rivers.nba.entity.TransactionRecord;
import com.rivers.nba.repository.TransactionRepository;
import com.rivers.nba.service.ITransactionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 交易记录表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class TransactionServiceImpl implements ITransactionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncTransactionsByDate(SyncTransactionsByDateReq req) {
        String date = req.getDate();
        return transactionRepository.logicDeleteByDate(date)
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "TransactionsByDate/" + date + "?key=" + nbaKey)))
                .map(result -> {
                    log.info("transactions date:{} result length:{}", date, StringUtils.length(result));
                    return Optional.ofNullable(result)
                            .map(i -> {
                                List<TransactionRecord> transactions = JSON.parseArray(i, TransactionRecord.class);
                                return transactions.stream()
                                        .map(t -> {
                                            TransactionRecord entity = new TransactionRecord();
                                            BeanUtils.copyProperties(t, entity);
                                            entity.setCreateUser("system");
                                            entity.setUpdateUser("system");
                                            return entity;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(transactions -> {
                    if (CollectionUtils.isEmpty(transactions)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return transactionRepository.saveAll(transactions).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetTransactionsRes>> getTransactionPage(GetTransactionsReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        String date = StringUtils.defaultIfBlank(req.getDate(), "");
        long offset = (long) (currentPage - 1) * pageSize;
        Mono<Long> countMono = transactionRepository.countAll();
        if (StringUtils.isNotBlank(date)) {
            countMono = transactionRepository.countByDate(date);
        }
        return countMono
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetTransactionsRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return getPageFlux(date, pageSize, offset)
                            .map(t -> TransactionInfo.newBuilder()
                                    .setPlayerId(defaultLong(t.getPlayerId()))
                                    .setName(defaultString(t.getName()))
                                    .setFormerTeamId(defaultInt(t.getFormerTeamId()))
                                    .setFormerTeam(defaultString(t.getFormerTeam()))
                                    .setTeamId(defaultInt(t.getTeamId()))
                                    .setTeam(defaultString(t.getTeam()))
                                    .setType(defaultString(t.getType()))
                                    .setDate(formatDate(t.getDate()))
                                    .setNote(defaultString(t.getNote()))
                                    .setCreated(formatDateTime(t.getCreated()))
                                    .setUpdated(formatDateTime(t.getUpdated()))
                                    .build())
                            .collectList()
                            .map(list -> GetTransactionsRes.newBuilder()
                                    .setTotal(total)
                                    .addAllTransactions(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    /**
     * 按条件选择分页查询流（date 非空按日期，否则全量）
     */
    private Flux<TransactionRecord> getPageFlux(String date, int pageSize, long offset) {
        if (StringUtils.isNotBlank(date)) {
            return transactionRepository.pageByDate(date, pageSize, offset);
        }
        return transactionRepository.pageAll(pageSize, offset);
    }

    /**
     * 日期格式化为 yyyy-MM-dd（空值用默认值替换）
     */
    private static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * 日期时间格式化为 yyyy-MM-dd HH:mm:ss（空值用默认值替换）
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 字符串空值用默认值替换（null 转空串）
     */
    private static String defaultString(String value) {
        if (value == null) {
            return "";
        }
        return value;
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

    /**
     * 长整数空值用默认值替换（null 转 0）
     */
    private static long defaultLong(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }
}

package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.CurrentSeasonInfo;
import com.rivers.nba.GetCurrentSeasonRes;
import com.rivers.nba.entity.CurrentSeason;
import com.rivers.nba.repository.CurrentSeasonRepository;
import com.rivers.nba.service.ICurrentSeasonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 当前赛季信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class CurrentSeasonServiceImpl implements ICurrentSeasonService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final CurrentSeasonRepository currentSeasonRepository;

    public CurrentSeasonServiceImpl(CurrentSeasonRepository currentSeasonRepository) {
        this.currentSeasonRepository = currentSeasonRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncCurrentSeason() {
        return currentSeasonRepository.countActive()
                .flatMap(count -> {
                    if (count > 0) {
                        return currentSeasonRepository.logicDeleteAll().then();
                    }
                    return Mono.empty();
                })
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "CurrentSeason?key=" + nbaKey)))
                .map(result -> {
                    log.info("current season result:{}", result);
                    if (result.isEmpty()) {
                        return null;
                    }
                    CurrentSeason season = JSON.parseObject(result, CurrentSeason.class);
                    CurrentSeason entity = new CurrentSeason();
                    BeanUtils.copyProperties(season, entity);
                    entity.setCreateUser("system");
                    entity.setUpdateUser("system");
                    return entity;
                })
                .flatMap(season ->
                        currentSeasonRepository.save(season).then(Mono.just(ResultVO.ok())));
    }

    @Override
    public Mono<ResultVO<GetCurrentSeasonRes>> getCurrentSeason() {
        return currentSeasonRepository.findActive()
                .map(s -> GetCurrentSeasonRes.newBuilder()
                        .setCurrentSeason(CurrentSeasonInfo.newBuilder()
                                .setSeason(defaultInt(s.getSeason()))
                                .setStartYear(defaultInt(s.getStartYear()))
                                .setEndYear(defaultInt(s.getEndYear()))
                                .setDescription(defaultString(s.getDescription()))
                                .setRegularSeasonStartDate(formatDate(s.getRegularSeasonStartDate()))
                                .setPostSeasonStartDate(formatDate(s.getPostSeasonStartDate()))
                                .setSeasonType(defaultString(s.getSeasonType()))
                                .setApiSeason(defaultString(s.getApiSeason()))
                                .build())
                        .build())
                .map(ResultVO::ok)
                .defaultIfEmpty(ResultVO.ok(GetCurrentSeasonRes.newBuilder().getDefaultInstanceForType()));
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
}

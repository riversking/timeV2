package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.CurrentSeasonInfo;
import com.rivers.nba.GetCurrentSeasonRes;
import com.rivers.nba.entity.CurrentSeason;
import com.rivers.nba.repository.CurrentSeasonRepository;
import com.rivers.nba.service.ICurrentSeasonService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    private final WebClientUtil webClientUtil;

    public CurrentSeasonServiceImpl(CurrentSeasonRepository currentSeasonRepository, WebClientUtil webClientUtil) {
        this.currentSeasonRepository = currentSeasonRepository;
        this.webClientUtil = webClientUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncCurrentSeason() {
        return currentSeasonRepository.countActive()
                .flatMap(this::deleteActiveSeasons)
                .then(webClientUtil.get(nbaUrl + "CurrentSeason?key=" + nbaKey))
                .flatMap(this::parseSeasonEntity)
                .flatMap(season -> currentSeasonRepository.save(season)
                        .then(Mono.just(ResultVO.<Void>ok())))
                .defaultIfEmpty(ResultVO.fail("未获取到当前赛季数据"));
    }

    private Mono<Void> deleteActiveSeasons(long count) {
        if (count == 0) {
            return Mono.empty();
        }
        return currentSeasonRepository.logicDeleteAll().then();
    }

    private Mono<CurrentSeason> parseSeasonEntity(String result) {
        log.info("current season result:{}", result);
        if (StringUtils.isEmpty(result)) {
            return Mono.empty();
        }
        CurrentSeason season = JSON.parseObject(result, CurrentSeason.class);
        if (season == null) {
            return Mono.empty();
        }
        CurrentSeason entity = new CurrentSeason();
        BeanUtils.copyProperties(season, entity);
        entity.setCreateUser("system");
        entity.setUpdateUser("system");
        return Mono.just(entity);
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

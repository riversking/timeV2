package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetStadiumDetailReq;
import com.rivers.nba.GetStadiumDetailRes;
import com.rivers.nba.GetStadiumsReq;
import com.rivers.nba.GetStadiumsRes;
import com.rivers.nba.Stadium;
import com.rivers.nba.entity.TimerStadium;
import com.rivers.nba.repository.StadiumRepository;
import com.rivers.nba.service.IStadiumService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 球馆信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class StadiumServiceImpl implements IStadiumService {

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final StadiumRepository stadiumRepository;

    public StadiumServiceImpl(StadiumRepository stadiumRepository) {
        this.stadiumRepository = stadiumRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncStadiums() {
        return stadiumRepository.countByName("")
                .flatMap(count -> {
                    if (count > 0) {
                        return stadiumRepository.logicDeleteAll().then();
                    }
                    return Mono.empty();
                })
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "Stadiums?key=" + nbaKey)))
                .map(result -> {
                    log.info("stadiums result length:{}", StringUtils.length(result));
                    return Optional.ofNullable(result)
                            .map(i -> {
                                List<TimerStadium> stadiums = JSON.parseArray(i, TimerStadium.class);
                                return stadiums.stream()
                                        .map(s -> {
                                            TimerStadium stadium = new TimerStadium();
                                            BeanUtils.copyProperties(s, stadium);
                                            stadium.setCreateUser("system");
                                            stadium.setUpdateUser("system");
                                            return stadium;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(stadiums -> {
                    if (CollectionUtils.isEmpty(stadiums)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return stadiumRepository.saveAll(stadiums).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetStadiumsRes>> getStadiumPage(GetStadiumsReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        String name = StringUtils.defaultIfBlank(req.getName(), "");
        long offset = (long) (currentPage - 1) * pageSize;
        return stadiumRepository.countByName(name)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetStadiumsRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return stadiumRepository.pageByName(name, pageSize, offset)
                            .map(this::toProto)
                            .collectList()
                            .map(list -> GetStadiumsRes.newBuilder()
                                    .setTotal(total)
                                    .addAllStadiums(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    @Override
    public Mono<ResultVO<GetStadiumDetailRes>> getStadiumDetail(GetStadiumDetailReq req) {
        return stadiumRepository.findByStadiumId(req.getStadiumId())
                .map(i -> ResultVO.ok(GetStadiumDetailRes.newBuilder().setStadium(toProto(i)).build()))
                .defaultIfEmpty(ResultVO.ok(GetStadiumDetailRes.newBuilder().getDefaultInstanceForType()));
    }

    /**
     * 实体转 proto 消息
     */
    private Stadium toProto(TimerStadium s) {
        return Stadium.newBuilder()
                .setStadiumId(defaultInt(s.getStadiumId()))
                .setActive(Boolean.TRUE.equals(s.getActive()))
                .setName(defaultString(s.getName()))
                .setAddress(defaultString(s.getAddress()))
                .setCity(defaultString(s.getCity()))
                .setState(defaultString(s.getState()))
                .setZip(defaultString(s.getZip()))
                .setCountry(defaultString(s.getCountry()))
                .setCapacity(defaultInt(s.getCapacity()))
                .setGeoLat(defaultDouble(s.getGeoLat()))
                .setGeoLong(defaultDouble(s.getGeoLong()))
                .build();
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
     * 浮点数空值用默认值替换（null 转 0.0）
     */
    private static double defaultDouble(Double value) {
        if (value == null) {
            return 0.0;
        }
        return value;
    }
}

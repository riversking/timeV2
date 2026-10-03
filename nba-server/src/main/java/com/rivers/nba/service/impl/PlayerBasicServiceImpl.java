package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerBasicsReq;
import com.rivers.nba.GetPlayerBasicsRes;
import com.rivers.nba.PlayerBasicInfo;
import com.rivers.nba.SyncPlayersByTeamReq;
import com.rivers.nba.entity.PlayerBasic;
import com.rivers.nba.repository.PlayerBasicRepository;
import com.rivers.nba.service.IPlayerBasicService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 球员简档信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class PlayerBasicServiceImpl implements IPlayerBasicService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final PlayerBasicRepository playerBasicRepository;

    public PlayerBasicServiceImpl(PlayerBasicRepository playerBasicRepository) {
        this.playerBasicRepository = playerBasicRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncActivePlayers() {
        return syncBySource(1, "PlayersActiveBasic");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncFreeAgents() {
        return syncBySource(2, "PlayersByFreeAgents");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncPlayersByTeam(SyncPlayersByTeamReq req) {
        return syncBySource(3, "PlayersBasic/" + req.getTeam());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncFreeAgentsFull() {
        return syncBySource(4, "FreeAgents");
    }

    private Mono<ResultVO<Void>> syncBySource(int source, String endpoint) {
        return playerBasicRepository.logicDeleteBySource(source)
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + endpoint + "?key=" + nbaKey)))
                .map(result -> {
                    log.info("player basic source:{} result length:{}", source, StringUtils.length(result));
                    return Optional.ofNullable(result)
                            .map(i -> {
                                List<PlayerBasic> list = JSON.parseArray(i, PlayerBasic.class);
                                return list.stream()
                                        .map(p -> {
                                            PlayerBasic entity = new PlayerBasic();
                                            BeanUtils.copyProperties(p, entity);
                                            entity.setSource(source);
                                            entity.setCreateUser("system");
                                            entity.setUpdateUser("system");
                                            return entity;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(list -> {
                    if (CollectionUtils.isEmpty(list)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return playerBasicRepository.saveAll(list).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetPlayerBasicsRes>> getPlayerBasicPage(GetPlayerBasicsReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        int source = resolveSource(req.getSource());
        String name = StringUtils.defaultIfBlank(req.getName(), "");
        long offset = (long) (currentPage - 1) * pageSize;
        return playerBasicRepository.countBySource(source, name)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetPlayerBasicsRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return playerBasicRepository.pageBySource(source, name, pageSize, offset)
                            .map(p -> PlayerBasicInfo.newBuilder()
                                    .setPlayerId(defaultLong(p.getPlayerId()))
                                    .setSportsDataId(defaultString(p.getSportsDataId()))
                                    .setStatus(defaultString(p.getStatus()))
                                    .setTeamId(defaultInt(p.getTeamId()))
                                    .setTeam(defaultString(p.getTeam()))
                                    .setJersey(defaultInt(p.getJersey()))
                                    .setPositionCategory(defaultString(p.getPositionCategory()))
                                    .setPosition(defaultString(p.getPosition()))
                                    .setFirstName(defaultString(p.getFirstName()))
                                    .setLastName(defaultString(p.getLastName()))
                                    .setHeight(defaultInt(p.getHeight()))
                                    .setWeight(defaultInt(p.getWeight()))
                                    .setBirthDate(toDateString(p.getBirthDate()))
                                    .setBirthCity(defaultString(p.getBirthCity()))
                                    .setBirthState(defaultString(p.getBirthState()))
                                    .setBirthCountry(defaultString(p.getBirthCountry()))
                                    .setCollege(defaultString(p.getCollege()))
                                    .setSalary(defaultInt(p.getSalary()))
                                    .setPhotoUrl(defaultString(p.getPhotoUrl()))
                                    .setExperience(defaultInt(p.getExperience()))
                                    .setDraftKingsName(defaultString(p.getDraftKingsName()))
                                    .setGlobalTeamId(defaultInt(p.getGlobalTeamId()))
                                    .setSource(defaultInt(p.getSource()))
                                    .build())
                            .collectList()
                            .map(list -> GetPlayerBasicsRes.newBuilder()
                                    .setTotal(total)
                                    .addAllPlayerBasics(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    /**
     * LocalDateTime 转日期字符串（yyyy-MM-dd），null 用默认值替换
     */
    private static String toDateString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * 解析有效的数据来源（非正数时取默认值 1）
     */
    private static int resolveSource(int source) {
        if (source <= 0) {
            return 1;
        }
        return source;
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

package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTeamDetailReq;
import com.rivers.nba.GetTeamDetailRes;
import com.rivers.nba.GetTeamsReq;
import com.rivers.nba.GetTeamsRes;
import com.rivers.nba.TeamInfo;
import com.rivers.nba.entity.Team;
import com.rivers.nba.repository.TeamRepository;
import com.rivers.nba.service.ITeamService;
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
 * 球队信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class TeamServiceImpl implements ITeamService {

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final TeamRepository teamRepository;

    public TeamServiceImpl(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncTeams() {
        return teamRepository.countByName("")
                .flatMap(count -> {
                    if (count > 0) {
                        return teamRepository.logicDeleteAll().then();
                    }
                    return Mono.empty();
                })
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "teams?key=" + nbaKey)))
                .map(result -> {
                    log.info("teams result length:{}", StringUtils.length(result));
                    return Optional.ofNullable(result)
                            .map(i -> {
                                List<Team> teams = JSON.parseArray(i, Team.class);
                                return teams.stream()
                                        .map(t -> {
                                            Team team = new Team();
                                            BeanUtils.copyProperties(t, team);
                                            team.setCreateUser("system");
                                            team.setUpdateUser("system");
                                            return team;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(teams -> {
                    if (CollectionUtils.isEmpty(teams)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return teamRepository.saveAll(teams).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetTeamsRes>> getTeamPage(GetTeamsReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        String name = StringUtils.defaultIfBlank(req.getName(), "");
        long offset = (long) (currentPage - 1) * pageSize;
        return teamRepository.countByName(name)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetTeamsRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return teamRepository.pageByName(name, pageSize, offset)
                            .map(this::toProto)
                            .collectList()
                            .map(list -> GetTeamsRes.newBuilder()
                                    .setTotal(total)
                                    .addAllTeams(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    @Override
    public Mono<ResultVO<GetTeamDetailRes>> getTeamDetail(GetTeamDetailReq req) {
        return teamRepository.findByTeamId(req.getTeamId())
                .map(i -> ResultVO.ok(GetTeamDetailRes.newBuilder().setTeam(toProto(i)).build()))
                .defaultIfEmpty(ResultVO.ok(GetTeamDetailRes.newBuilder().getDefaultInstanceForType()));
    }

    /**
     * 实体转 proto 消息
     */
    private TeamInfo toProto(Team t) {
        return TeamInfo.newBuilder()
                .setTeamId(defaultInt(t.getTeamId()))
                .setKey(defaultString(t.getKey()))
                .setActive(Boolean.TRUE.equals(t.getActive()))
                .setCity(defaultString(t.getCity()))
                .setName(defaultString(t.getName()))
                .setLeagueId(defaultInt(t.getLeagueId()))
                .setStadiumId(defaultInt(t.getStadiumId()))
                .setConference(defaultString(t.getConference()))
                .setDivision(defaultString(t.getDivision()))
                .setPrimaryColor(defaultString(t.getPrimaryColor()))
                .setSecondaryColor(defaultString(t.getSecondaryColor()))
                .setTertiaryColor(defaultString(t.getTertiaryColor()))
                .setQuaternaryColor(defaultString(t.getQuaternaryColor()))
                .setWikipediaLogoUrl(defaultString(t.getWikipediaLogoUrl()))
                .setGlobalTeamId(defaultInt(t.getGlobalTeamId()))
                .setNbaDotComTeamId(defaultInt(t.getNbaDotComTeamId()))
                .setHeadCoach(defaultString(t.getHeadCoach()))
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
}

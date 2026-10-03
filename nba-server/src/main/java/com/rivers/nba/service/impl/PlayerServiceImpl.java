package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerDetailReq;
import com.rivers.nba.GetPlayerDetailRes;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;
import com.rivers.nba.Player;
import com.rivers.nba.client.UserServiceClient;
import com.rivers.nba.entity.TimerPlayer;
import com.rivers.nba.repository.PlayerRepository;
import com.rivers.nba.service.IPlayerService;
import com.rivers.proto.DicDataReq;
import com.rivers.proto.DicDataRes;
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
 * 球员信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2024-06-16
 */
@Slf4j
@Service
public class PlayerServiceImpl implements IPlayerService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final PlayerRepository playerRepository;

    private final UserServiceClient userServiceClient;

    public PlayerServiceImpl(PlayerRepository playerRepository, UserServiceClient userServiceClient) {
        this.playerRepository = playerRepository;
        this.userServiceClient = userServiceClient;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncAllPlayer() {
        return playerRepository.countByName("")
                .flatMap(count -> {
                    if (count > 0) {
                        return playerRepository.logicDeleteAll().then();
                    }
                    return Mono.empty();
                })
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "Players?key=" + nbaKey)))
                .map(result -> {
                    log.info("result:{}", result);
                    return Optional.ofNullable(result)
                            .map(i -> {
                                JSONArray array = JSON.parseArray(i);
                                return array.stream()
                                        .map(item -> {
                                            JSONObject jo = (JSONObject) item;
                                            TimerPlayer p = jo.toJavaObject(TimerPlayer.class);
                                            TimerPlayer player = new TimerPlayer();
                                            BeanUtils.copyProperties(p, player);
                                            player.setHeight((int) (p.getHeight() * 2.5));
                                            // sportsdata 的 PhotoUrl 为 0.png 占位图，改用 NBA 官方头像 CDN（基于 NbaDotComPlayerID）
                                            Long nbaDotComPlayerId = jo.getLong("NbaDotComPlayerID");
                                            player.setPhotoUrl("");
                                            if (nbaDotComPlayerId != null && nbaDotComPlayerId > 0) {
                                                player.setPhotoUrl("https://cdn.nba.com/headshots/nba/latest/260x190/" + nbaDotComPlayerId + ".png");
                                            }
                                            player.setCreateUser("system");
                                            player.setUpdateUser("system");
                                            return player;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(nowPlayers -> {
                    if (CollectionUtils.isEmpty(nowPlayers)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return playerRepository.saveAll(nowPlayers).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetPlayersRes>> getPlayerPage(GetPlayersReq getPlayersReq) {
        int currentPage = getPlayersReq.getCurrentPage();
        int pageSize = getPlayersReq.getPageSize();
        String playerName = getPlayersReq.getPlayerName();
        String name = StringUtils.defaultIfBlank(playerName, "");
        long offset = (long) (currentPage - 1) * pageSize;
        userServiceClient.getDicData(DicDataReq.newBuilder().setDicKey("sex").build())
                .subscribe(i -> {
                    DicDataRes data = i.getData();
                    log.info("data {}", data);
                }, e -> log.warn("字典查询失败: {}", e.getMessage()));
        return playerRepository.countByName(name)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetPlayersRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return playerRepository.pageByName(name, pageSize, offset)
                            .map(this::toProto)
                            .collectList()
                            .map(list -> GetPlayersRes.newBuilder()
                                    .setTotal(total)
                                    .addAllPlayers(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    @Override
    public Mono<ResultVO<GetPlayerDetailRes>> getPlayerDetail(GetPlayerDetailReq req) {
        return playerRepository.findByPlayerId(req.getPlayerId())
                .map(i -> ResultVO.ok(GetPlayerDetailRes.newBuilder().setPlayer(toProto(i)).build()))
                .defaultIfEmpty(ResultVO.ok(GetPlayerDetailRes.newBuilder().getDefaultInstanceForType()));
    }

    /**
     * 实体转 proto 消息
     */
    private Player toProto(TimerPlayer i) {
        return Player.newBuilder()
                .setPlayerId(i.getPlayerId())
                .setTeam(i.getTeam())
                .setTeamId(i.getTeamId())
                .setJersey(i.getJersey())
                .setStatus(i.getStatus())
                .setPositionCategory(i.getPositionCategory())
                .setPosition(i.getPosition())
                .setFirstName(i.getFirstName())
                .setLastName(i.getLastName())
                .setHeight(i.getHeight())
                .setWeight(i.getWeight())
                .setBirthDate(toDateString(i.getBirthDate()))
                .setBirthCity(i.getBirthCity())
                .setBirthState(i.getBirthState())
                .setBirthCountry(i.getBirthCountry())
                .setCollege(i.getCollege())
                .setSalary(i.getSalary())
                .setPhotoUrl(i.getPhotoUrl())
                .setExperience(i.getExperience())
                .setDraftKingsName(i.getDraftKingsName())
                .build();
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
}

package com.rivers.nba.service.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;
import com.rivers.nba.Player;
import com.rivers.nba.entity.TimerPlayer;
import com.rivers.nba.mapper.PlayerMapper;
import com.rivers.nba.service.IPlayerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final PlayerMapper playerMapper;

    public PlayerServiceImpl(PlayerMapper playerMapper) {
        this.playerMapper = playerMapper;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> syncAllPlayer() {
        List<TimerPlayer> allPlayers = playerMapper.selectList(Wrappers.emptyWrapper());
        if (CollectionUtils.isNotEmpty(allPlayers)) {
            playerMapper.delete(Wrappers.emptyWrapper());
        }
        CompletableFuture<String> async = HttpClientUtil.getAsync(nbaUrl + "Players?key=" + nbaKey);
        String result = async.join();
        log.info("result:{}", result);
        List<TimerPlayer> nowPlayers = Optional.ofNullable(result)
                .map(i -> {
                    List<TimerPlayer> players = JSON.parseArray(i, TimerPlayer.class);
                    return players.stream()
                            .map(p -> {
                                TimerPlayer player = new TimerPlayer();
                                BeanUtils.copyProperties(p, player);
                                player.setHeight((int) (p.getHeight() * 2.5));
                                player.setCreateUser("system");
                                player.setUpdateUser("system");
                                return player;
                            })
                            .toList();
                }).orElse(null);
        if (CollectionUtils.isEmpty(nowPlayers)) {
            return ResultVO.ok();
        }
        playerMapper.insert(nowPlayers, 1000);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<GetPlayersRes> getPlayerPage(GetPlayersReq getPlayersReq) {
        int currentPage = getPlayersReq.getCurrentPage();
        int pageSize = getPlayersReq.getPageSize();
        String playerName = getPlayersReq.getPlayerName();
        LambdaQueryWrapper<TimerPlayer> playerWrapper = Wrappers.lambdaQuery();
        playerWrapper.like(StringUtils.isNotBlank(playerName), TimerPlayer::getDraftKingsName, playerName);
        Page<TimerPlayer> page = Page.of(currentPage, pageSize);
        Page<TimerPlayer> playerPage = playerMapper.selectPage(page, playerWrapper);
        long total = playerPage.getTotal();
        if (total == 0) {
            return ResultVO.ok(GetPlayersRes.newBuilder().getDefaultInstanceForType());
        }

        List<TimerPlayer> records = playerPage.getRecords();
        List<Player> list = records.stream()
                .map(i ->
                        Player.newBuilder()
                                .setPlayerId(i.getPlayerId())
                                .setBirthCity(i.getBirthCity())
                                .setBirthDate(DateUtil.formatDate(i.getBirthDate()))
                                .setCollege(i.getCollege())
                                .setExperience(i.getExperience())
                                .setHeight(i.getHeight())
                                .setWeight(i.getWeight())
                                .setDraftKingsName(i.getDraftKingsName())
                                .setPosition(i.getPosition())
                                .setSalary(i.getSalary())
                                .setTeam(i.getTeam())
                                .build()
                ).toList();
        GetPlayersRes getPlayersRes = GetPlayersRes.newBuilder()
                .setTotal(total)
                .addAllPlayers(list)
                .build();
        return ResultVO.ok(getPlayersRes);
    }
}

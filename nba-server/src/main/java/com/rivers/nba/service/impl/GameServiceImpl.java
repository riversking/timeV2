package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GameDayCount;
import com.rivers.nba.GameInfo;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import com.rivers.nba.entity.Game;
import com.rivers.nba.repository.GameRepository;
import com.rivers.nba.service.IGameService;
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
 * 比赛信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class GameServiceImpl implements IGameService {

    private static final int RECENT_LIMIT = 6;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final GameRepository gameRepository;

    public GameServiceImpl(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncGamesBySeason(SyncGamesBySeasonReq req) {
        int season = req.getSeason();
        return gameRepository.logicDeleteBySeason(season)
                .then(Mono.fromFuture(() ->
                        HttpClientUtil.getAsync(nbaUrl + "Games/" + season + "NBA?key=" + nbaKey)))
                .flatMap(this::saveGames);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncGamesByDate(SyncGamesByDateReq req) {
        String date = req.getDate();
        return gameRepository.logicDeleteByDay(date)
                .then(Mono.fromFuture(() ->
                        HttpClientUtil.getAsync(nbaUrl + "GamesByDate/" + date + "?key=" + nbaKey)))
                .flatMap(this::saveGames);
    }

    private Mono<ResultVO<Void>> saveGames(String result) {
        log.info("games result length:{}", StringUtils.length(result));
        List<Game> games = Optional.ofNullable(result)
                .map(i -> {
                    List<Game> list = JSON.parseArray(i, Game.class);
                    return list.stream()
                            .map(g -> {
                                Game game = new Game();
                                BeanUtils.copyProperties(g, game);
                                game.setCreateUser("system");
                                game.setUpdateUser("system");
                                return game;
                            })
                            .toList();
                }).orElse(null);
        if (CollectionUtils.isEmpty(games)) {
            return Mono.just(ResultVO.ok());
        }
        return gameRepository.saveAll(games).then(Mono.just(ResultVO.ok()));
    }

    @Override
    public Mono<ResultVO<GetGamesRes>> getGamePage(GetGamesReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        long offset = (long) (currentPage - 1) * pageSize;
        int season = req.getSeason();
        int teamId = req.getTeamId();
        String date = StringUtils.defaultIfBlank(req.getDate(), "");
        return gameRepository.countByFilter(teamId, season, date)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetGamesRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return gameRepository.pageByFilter(teamId, season, date, pageSize, offset)
                            .map(this::toGameInfo)
                            .collectList()
                            .map(list -> GetGamesRes.newBuilder()
                                    .setTotal(total)
                                    .addAllGames(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    @Override
    public Mono<ResultVO<GetGamesRes>> getRecentGames() {
        return gameRepository.findUpcoming(RECENT_LIMIT)
                .map(this::toGameInfo)
                .collectList()
                .map(list -> ResultVO.ok(GetGamesRes.newBuilder()
                        .setTotal(list.size())
                        .addAllGames(list)
                        .build()));
    }

    @Override
    public Mono<ResultVO<GetGameCalendarRes>> getGameCalendar(GetGameCalendarReq req) {
        int season = req.getSeason();
        int teamId = req.getTeamId();
        String month = StringUtils.defaultIfBlank(req.getMonth(), "");
        return gameRepository.findByMonth(month, season, teamId)
                .map(g -> formatDate(g.getDay()))
                .filter(StringUtils::isNotBlank)
                .collectMultimap(day -> day)
                .map(dayMap -> {
                    GetGameCalendarRes.Builder builder = GetGameCalendarRes.newBuilder();
                    dayMap.forEach((day, days) -> builder.addDays(GameDayCount.newBuilder()
                            .setDay(day)
                            .setCount(days.size())
                            .build()));
                    return ResultVO.ok(builder.build());
                });
    }

    /**
     * 实体转 proto 消息（日期用 DateTimeFormatter 格式化，空值用默认值替换）
     */
    private GameInfo toGameInfo(Game g) {
        return GameInfo.newBuilder()
                .setGameId(defaultInt(g.getGameId()))
                .setSeason(defaultInt(g.getSeason()))
                .setSeasonType(defaultInt(g.getSeasonType()))
                .setStatus(defaultString(g.getStatus()))
                .setDay(formatDate(g.getDay()))
                .setDateTime(formatDateTime(g.getDateTime()))
                .setAwayTeam(defaultString(g.getAwayTeam()))
                .setHomeTeam(defaultString(g.getHomeTeam()))
                .setAwayTeamId(defaultInt(g.getAwayTeamId()))
                .setHomeTeamId(defaultInt(g.getHomeTeamId()))
                .setStadiumId(defaultInt(g.getStadiumId()))
                .setChannel(defaultString(g.getChannel()))
                .setAttendance(defaultInt(g.getAttendance()))
                .setAwayTeamScore(defaultInt(g.getAwayTeamScore()))
                .setHomeTeamScore(defaultInt(g.getHomeTeamScore()))
                .setUpdated(formatDateTime(g.getUpdated()))
                .setIsClosed(Boolean.TRUE.equals(g.getIsClosed()))
                .setGameEndDateTime(formatDateTime(g.getGameEndDateTime()))
                .setNeutralVenue(Boolean.TRUE.equals(g.getNeutralVenue()))
                .setDateTimeUtc(formatDateTime(g.getDateTimeUtc()))
                .setInseasonTournament(Boolean.TRUE.equals(g.getInseasonTournament()))
                .setGlobalGameId(defaultInt(g.getGlobalGameId()))
                .build();
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
}

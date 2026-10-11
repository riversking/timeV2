package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GameDayCount;
import com.rivers.nba.GameInfo;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.ScoreReportReq;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import com.rivers.nba.entity.Game;
import com.rivers.nba.repository.GameRepository;
import com.rivers.nba.service.IGameService;
import com.rivers.nba.service.ScorePushService;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final ScorePushService scorePushService;

    private final WebClientUtil webClientUtil;

    public GameServiceImpl(GameRepository gameRepository, ScorePushService scorePushService, WebClientUtil webClientUtil) {
        this.gameRepository = gameRepository;
        this.scorePushService = scorePushService;
        this.webClientUtil = webClientUtil;
    }

    /**
     * 整赛季同步：作为赛程骨架来源使用（该端点位于数据源演示环境，比分字段为模拟值），
     * 真实比分需按日调用 syncGamesByDate 修正
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncGamesBySeason(SyncGamesBySeasonReq req) {
        int season = req.getSeason();
        return webClientUtil.get(nbaUrl + "Games/" + season + "NBA?key=" + nbaKey)
                .flatMap(this::saveGames);
    }

    /**
     * 按日同步比赛：赛程骨架取自 GamesByDate（对阵/球馆/频道等完整字段），
     * 比分用 ScoresBasic 的真实比分覆盖后入库（演示环境下 GamesByDate 的比分是模拟值，
     * ScoresBasic 才返回真实比分）；ScoresBasic 拉取失败时降级为按赛程原始数据同步，不阻断赛程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncGamesByDate(SyncGamesByDateReq req) {
        String date = req.getDate();
        return webClientUtil.get(nbaUrl + "GamesByDate/" + date + "?key=" + nbaKey)
                .flatMap(scheduleJson -> webClientUtil.get(nbaUrl + "ScoresBasic/" + date + "?key=" + nbaKey)
                        .onErrorResume(e -> {
                            log.warn("ScoresBasic 获取失败，本次按赛程原始数据同步: date={}, error={}", date, e.getMessage());
                            return Mono.just("[]");
                        })
                        .map(scoreJson -> mergeRealScores(scheduleJson, scoreJson)))
                .flatMap(this::saveGames);
    }

    /**
     * 合并赛程与真实比分：以赛程列表为基准，按 game_id 对齐 ScoresBasic 的真实比分，
     * 覆盖比分/状态/完场旗标/更新时间/完场时间（赛程结构字段保持 GamesByDate 来源）
     */
    private List<Game> mergeRealScores(String scheduleJson, String scoreJson) {
        List<Game> schedules = parseGames(scheduleJson);
        List<Game> scores = parseGames(scoreJson);
        if (CollectionUtils.isEmpty(scores)) {
            return schedules;
        }
        Map<Integer, Game> scoreMap = scores.stream()
                .filter(game -> game.getGameId() != null)
                .collect(Collectors.toMap(Game::getGameId, Function.identity(), (first, second) -> first));
        for (Game schedule : schedules) {
            Game real = scoreMap.get(schedule.getGameId());
            if (real == null) {
                continue;
            }
            schedule.setAwayTeamScore(real.getAwayTeamScore());
            schedule.setHomeTeamScore(real.getHomeTeamScore());
            schedule.setStatus(real.getStatus());
            schedule.setIsClosed(real.getIsClosed());
            if (real.getUpdated() != null) {
                schedule.setUpdated(real.getUpdated());
            }
            if (real.getGameEndDateTime() != null) {
                schedule.setGameEndDateTime(real.getGameEndDateTime());
            }
        }
        return schedules;
    }

    /**
     * 外部数据按 game_id 做 upsert：已存在的比赛更新为最新数据（比分/状态等随外部源刷新），
     * 不存在的比赛插入；同步全程不删除任何已有数据，保证历史赛程与比分持续留存
     */
    private Mono<ResultVO<Void>> saveGames(String result) {
        return saveGames(parseGames(result));
    }

    /**
     * 比赛列表 upsert：逐场按 game_id 更新或插入
     */
    private Mono<ResultVO<Void>> saveGames(List<Game> games) {
        if (CollectionUtils.isEmpty(games)) {
            return Mono.just(ResultVO.ok());
        }
        return Flux.fromIterable(games)
                .concatMap(this::upsertGame)
                .then(Mono.just(ResultVO.ok()));
    }

    /**
     * 解析外部返回的 JSON 为 Game 列表并补齐审计字段（解析失败按空处理）
     */
    private List<Game> parseGames(String result) {
        log.info("games result length:{}", StringUtils.length(result));
        List<Game> parsed = Optional.ofNullable(result)
                .map(i -> JSON.parseArray(i, Game.class))
                .orElse(List.of());
        if (parsed == null) {
            return List.of();
        }
        return parsed.stream()
                .map(g -> {
                    Game game = new Game();
                    BeanUtils.copyProperties(g, game);
                    game.setCreateUser("system");
                    game.setUpdateUser("system");
                    return game;
                })
                .toList();
    }

    /**
     * 单场比赛 upsert：按 game_id 查有效记录，存在则显式 UPDATE（项目 R2DBC 实体无 @Id 注解，
     * save() 对存量记录会误走 INSERT，更新必须使用显式 UPDATE 语句），不存在则插入
     */
    private Mono<Void> upsertGame(Game game) {
        Integer gameId = game.getGameId();
        if (gameId == null || gameId == 0) {
            log.warn("比赛数据缺少 gameId，跳过同步: {}", game);
            return Mono.empty();
        }
        return gameRepository.findByGameId(gameId)
                .flatMap(existing -> gameRepository.updateGame(gameId,
                        defaultInt(game.getSeason()), defaultInt(game.getSeasonType()), defaultString(game.getStatus()),
                        game.getDay(), game.getDateTime(), defaultString(game.getAwayTeam()),
                        defaultString(game.getHomeTeam()), defaultInt(game.getAwayTeamId()),
                        defaultInt(game.getHomeTeamId()), defaultInt(game.getStadiumId()),
                        defaultString(game.getChannel()), game.getAttendance(), game.getAwayTeamScore(),
                        game.getHomeTeamScore(), game.getUpdated(), Boolean.TRUE.equals(game.getIsClosed()),
                        game.getGameEndDateTime(), Boolean.TRUE.equals(game.getNeutralVenue()),
                        game.getDateTimeUtc(), Boolean.TRUE.equals(game.getInseasonTournament()),
                        defaultInt(game.getGlobalGameId()), "system")
                        .thenReturn(game))
                .switchIfEmpty(Mono.defer(() -> gameRepository.save(game)))
                .then();
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
    public Mono<ResultVO<GameInfo>> reportScoreChange(ScoreReportReq req) {
        return gameRepository.findByGameId(req.getGameId())
                .flatMap(game -> {
                    game.setAwayTeamScore(req.getAwayTeamScore());
                    game.setHomeTeamScore(req.getHomeTeamScore());
                    game.setIsClosed(req.getIsClosed());
                    game.setUpdateUser("system");
                    return gameRepository.updateScore(req.getGameId(),
                                    defaultInt(game.getAwayTeamScore()), defaultInt(game.getHomeTeamScore()),
                                    game.getStatus(), Boolean.TRUE.equals(game.getIsClosed()), game.getUpdated(),
                                    game.getUpdateUser())
                            .then(Mono.just(game));
                })
                .doOnNext(scorePushService::broadcastScoreChange)
                .map(game -> ResultVO.ok(toGameInfo(game)))
                .switchIfEmpty(Mono.just(ResultVO.<GameInfo>fail("比赛不存在: " + req.getGameId())));
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

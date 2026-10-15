package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GameDayCount;
import com.rivers.nba.GameDetail;
import com.rivers.nba.GameInfo;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGameDetailReq;
import com.rivers.nba.GetGameDetailRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.PeriodScore;
import com.rivers.nba.PlayerGameStatLine;
import com.rivers.nba.ScoreReportReq;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import com.rivers.nba.TeamBoxScore;
import com.rivers.nba.client.EspnScoreboardClient;
import com.rivers.nba.client.EspnScoreboardClient.EspnGame;
import com.rivers.nba.client.EspnScoreboardClient.EspnGameDetail;
import com.rivers.nba.client.EspnScoreboardClient.EspnTeamBox;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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

    /** 时间线默认每批条数（请求未指定或非法时兜底） */
    private static final int DEFAULT_TIMELINE_LIMIT = 20;

    private static final String TIMELINE_FORWARD = "forward";

    private static final String TIMELINE_BACKWARD = "backward";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ZoneId GAME_ZONE = ZoneId.of("America/New_York");

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final GameRepository gameRepository;

    private final ScorePushService scorePushService;

    private final WebClientUtil webClientUtil;

    private final EspnScoreboardClient espnScoreboardClient;

    public GameServiceImpl(GameRepository gameRepository, ScorePushService scorePushService,
                           WebClientUtil webClientUtil, EspnScoreboardClient espnScoreboardClient) {
        this.gameRepository = gameRepository;
        this.scorePushService = scorePushService;
        this.webClientUtil = webClientUtil;
        this.espnScoreboardClient = espnScoreboardClient;
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
     * 比分用 ESPN 计分板的真实比分覆盖后入库（演示环境下 GamesByDate 的比分是模拟值，
     * 需以真实数据源校正；ESPN 免密钥无配额，原 ScoresBasic 按调用计配额已弃用）；
     * ESPN 拉取失败时降级为按赛程原始数据同步，不阻断赛程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncGamesByDate(SyncGamesByDateReq req) {
        String date = req.getDate();
        return webClientUtil.get(nbaUrl + "GamesByDate/" + date + "?key=" + nbaKey)
                .flatMap(scheduleJson -> espnScoreboardClient.fetchGames(date)
                        .map(espnGames -> mergeEspnScores(scheduleJson, espnGames)))
                .flatMap(this::saveGames);
    }

    /**
     * 合并赛程与 ESPN 真实比分：对阵按「美东比赛日 + 主客队缩写」对齐（game_id 不同源无法直接关联），
     * 覆盖比分/状态/完场旗标（球馆/频道等赛程结构字段保持 GamesByDate 来源）
     */
    private List<Game> mergeEspnScores(String scheduleJson, List<EspnGame> espnGames) {
        List<Game> schedules = parseGames(scheduleJson);
        if (CollectionUtils.isEmpty(espnGames)) {
            return schedules;
        }
        Map<String, EspnGame> espnMap = espnGames.stream()
                .collect(Collectors.toMap(EspnGame::matchKey, Function.identity(), (first, second) -> first));
        for (Game schedule : schedules) {
            if (schedule.getDay() == null) {
                continue;
            }
            EspnGame real = espnMap.get(EspnScoreboardClient.matchKey(schedule.getDay().toLocalDate(),
                    schedule.getAwayTeam(), schedule.getHomeTeam()));
            if (real == null) {
                continue;
            }
            schedule.setAwayTeamScore(real.awayScore());
            schedule.setHomeTeamScore(real.homeScore());
            schedule.setStatus(real.status());
            schedule.setIsClosed(real.closed());
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
        String timelineMode = StringUtils.defaultIfBlank(req.getTimelineMode(), "");
        if (StringUtils.isBlank(timelineMode)) {
            return getGamePaged(req);
        }
        if (!TIMELINE_FORWARD.equals(timelineMode) && !TIMELINE_BACKWARD.equals(timelineMode)) {
            return Mono.just(ResultVO.<GetGamesRes>fail("不支持的时间线模式: " + timelineMode));
        }
        return getGameTimeline(req, TIMELINE_FORWARD.equals(timelineMode));
    }

    private Mono<ResultVO<GetGamesRes>> getGamePaged(GetGamesReq req) {
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

    /**
     * 赛程时间线：以锚点日（中国观赛日）为起点、游标 (cursorDay, cursorId) 行级翻页。
     * forward 取锚点日及以后按比赛日正序，backward 取锚点日以前按倒序（反转为正序返回）
     */
    private Mono<ResultVO<GetGamesRes>> getGameTimeline(GetGamesReq req, boolean forward) {
        int teamId = req.getTeamId();
        int season = req.getSeason();
        int limit = req.getPageSize() > 0 ? req.getPageSize() : DEFAULT_TIMELINE_LIMIT;
        String anchorDay = StringUtils.defaultIfBlank(req.getAnchorDay(),
                LocalDate.now(CHINA_ZONE).format(DATE_FORMATTER));
        String cursorDay = StringUtils.defaultIfBlank(req.getCursorDay(), "");
        long cursorId = req.getCursorId();
        Flux<Game> query = forward
                ? gameRepository.timelineForward(teamId, season, anchorDay, cursorDay, cursorId, limit)
                : gameRepository.timelineBackward(teamId, season, anchorDay, cursorDay, cursorId, limit);
        return query.map(this::toGameInfo)
                .collectList()
                .map(list -> {
                    List<GameInfo> ordered = new ArrayList<>(list);
                    if (!forward) {
                        Collections.reverse(ordered);
                    }
                    return ResultVO.ok(GetGamesRes.newBuilder()
                            .setTotal(ordered.size())
                            .addAllGames(ordered)
                            .build());
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
                .map(g -> formatChinaDay(g.getDay()))
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
     * 比赛详情：基础信息取库内（任何比赛都可打开页面），每节比分与球员数据实时取 ESPN；
     * 无比赛日或未匹配到 ESPN 比赛（如全明星赛）时仅返回基础信息
     */
    @Override
    public Mono<ResultVO<GetGameDetailRes>> getGameDetail(GetGameDetailReq req) {
        int gameId = req.getGameId();
        return gameRepository.findByGameId(gameId)
                .flatMap(game -> loadGameDetail(game)
                        .map(detail -> ResultVO.ok(GetGameDetailRes.newBuilder().setDetail(detail).build())))
                .switchIfEmpty(Mono.just(ResultVO.<GetGameDetailRes>fail("比赛不存在: " + gameId)));
    }

    private Mono<GameDetail> loadGameDetail(Game game) {
        GameInfo info = toGameInfo(game);
        if (game.getDay() == null) {
            return Mono.just(GameDetail.newBuilder().setGame(info).build());
        }
        return espnScoreboardClient.fetchGameDetail(game.getDay().toLocalDate(),
                        game.getAwayTeam(), game.getHomeTeam())
                .map(detail -> buildGameDetail(game, info, detail))
                .defaultIfEmpty(GameDetail.newBuilder().setGame(info).build());
    }

    /**
     * 组装详情：每节比分加总节数对齐（缺项补 0），球员数据表按归一后的缩写归属主客队
     */
    private static GameDetail buildGameDetail(Game game, GameInfo info, EspnGameDetail detail) {
        GameDetail.Builder builder = GameDetail.newBuilder().setGame(info);
        builder.addAllPeriods(buildPeriods(detail.game()));
        for (EspnTeamBox box : detail.boxes()) {
            TeamBoxScore boxScore = toTeamBoxScore(box);
            if (box.team().equals(game.getAwayTeam())) {
                builder.setAwayBox(boxScore);
            } else if (box.team().equals(game.getHomeTeam())) {
                builder.setHomeBox(boxScore);
            }
        }
        return builder.build();
    }

    private static List<PeriodScore> buildPeriods(EspnGame espnGame) {
        List<Integer> away = espnGame.awayPeriods();
        List<Integer> home = espnGame.homePeriods();
        int size = Math.max(away.size(), home.size());
        List<PeriodScore> periods = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            periods.add(PeriodScore.newBuilder()
                    .setPeriod(i + 1)
                    .setAwayScore(i < away.size() ? away.get(i) : 0)
                    .setHomeScore(i < home.size() ? home.get(i) : 0)
                    .build());
        }
        return periods;
    }

    private static TeamBoxScore toTeamBoxScore(EspnTeamBox box) {
        return TeamBoxScore.newBuilder()
                .setTeam(box.team())
                .addAllColumns(box.columns())
                .addAllPlayers(box.players().stream()
                        .map(line -> PlayerGameStatLine.newBuilder()
                                .setName(line.name())
                                .setStarter(line.starter())
                                .setPlayed(line.played())
                                .addAllStats(line.stats())
                                .build())
                        .toList())
                .build();
    }

    /**
     * 实体转 proto 消息（比赛日 / 开赛时间 / 完场时间转中国观赛口径：日期 + 1 天、时间转北京时间；空值用默认值替换）
     */
    private GameInfo toGameInfo(Game g) {
        return GameInfo.newBuilder()
                .setGameId(defaultInt(g.getGameId()))
                .setId(defaultLong(g.getId()))
                .setSeason(defaultInt(g.getSeason()))
                .setSeasonType(defaultInt(g.getSeasonType()))
                .setStatus(defaultString(g.getStatus()))
                .setDay(formatChinaDay(g.getDay()))
                .setDateTime(formatChinaDateTime(g.getDateTime()))
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
                .setGameEndDateTime(formatChinaDateTime(g.getGameEndDateTime()))
                .setNeutralVenue(Boolean.TRUE.equals(g.getNeutralVenue()))
                .setDateTimeUtc(formatDateTime(g.getDateTimeUtc()))
                .setInseasonTournament(Boolean.TRUE.equals(g.getInseasonTournament()))
                .setGlobalGameId(defaultInt(g.getGlobalGameId()))
                .build();
    }

    /**
     * 比赛日转中国观赛日期（美东比赛日 + 1 天：NBA 比赛北京时间均在美东次日凌晨至中午开赛）
     */
    private static String formatChinaDay(LocalDateTime gameDay) {
        if (gameDay == null) {
            return "";
        }
        return gameDay.plusDays(1).format(DATE_FORMATTER);
    }

    /**
     * 美东时间转北京时间（夏令时/冬令时自动处理，如美东 20:30 = 北京时间次日 08:30）
     */
    private static String formatChinaDateTime(LocalDateTime gameTime) {
        if (gameTime == null) {
            return "";
        }
        return gameTime.atZone(GAME_ZONE).withZoneSameInstant(CHINA_ZONE).toLocalDateTime().format(DATE_TIME_FORMATTER);
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

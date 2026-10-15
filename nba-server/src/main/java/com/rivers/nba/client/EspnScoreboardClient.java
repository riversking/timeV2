package com.rivers.nba.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.client.WebClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <p>
 * ESPN 公开计分板客户端：免密钥、无配额（SportsData 按调用计配额，高频拉取不可持续）。
 * dates 按美东比赛日归档；库内 gameId 属 SportsData 体系与 ESPN event.id 不同源，
 * 对阵统一以「美东比赛日 + 主客队缩写」为匹配键，两端的缩写差异经内置映射归一
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
@Slf4j
@Component
public class EspnScoreboardClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ZoneId GAME_ZONE = ZoneId.of("America/New_York");

    private static final String SCOREBOARD_URL =
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=";

    /**
     * 单场比赛概要接口（球员技术统计等 scoreboard 没有的明细，按 ESPN event.id 查询）
     */
    private static final String SUMMARY_URL =
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=";

    /**
     * ESPN 与库内（SportsData 缩写体系）不一致的球队缩写映射，其余缩写两端相同
     */
    private static final Map<String, String> ESPN_TO_DB_TEAM = Map.of(
            "WSH", "WAS",
            "UTAH", "UTA",
            "PHX", "PHO");

    /**
     * 比分无意义的状态（比赛未实际进行，ESPN 可能返回 0 分，按 null 处理）
     */
    private static final Set<String> NO_SCORE_STATUSES = Set.of("Postponed", "Canceled");

    private final WebClientUtil webClientUtil;

    public EspnScoreboardClient(WebClientUtil webClientUtil) {
        this.webClientUtil = webClientUtil;
    }

    /**
     * 拉取指定美东比赛日的比赛（失败按空列表处理，调用方自行决定降级策略）
     */
    public Mono<List<EspnGame>> fetchGames(String etDate) {
        return webClientUtil.get(SCOREBOARD_URL + etDate.replace("-", ""))
                .map(this::parseGames)
                .onErrorResume(e -> {
                    log.warn("ESPN 计分板拉取失败: date={}, error={}", etDate, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    /**
     * 拉取单场比赛详情（每节比分取计分板、球员技术统计取概要接口）。
     * 计分板按比赛日归档，先用匹配键定位 ESPN 比赛；概要接口失败时仍返回每节比分与空数据表，
     * 未匹配到 ESPN 比赛（如全明星赛）返回空
     */
    public Mono<EspnGameDetail> fetchGameDetail(LocalDate day, String awayTeam, String homeTeam) {
        String key = matchKey(day, awayTeam, homeTeam);
        return fetchGames(day.format(DATE_FORMATTER))
                .flatMap(games -> games.stream()
                        .filter(game -> key.equals(game.matchKey()))
                        .findFirst()
                        .map(this::fetchSummary)
                        .orElseGet(Mono::empty));
    }

    private Mono<EspnGameDetail> fetchSummary(EspnGame game) {
        if (StringUtils.isBlank(game.eventId())) {
            return Mono.just(new EspnGameDetail(game, List.of()));
        }
        return webClientUtil.get(SUMMARY_URL + game.eventId())
                .map(json -> new EspnGameDetail(game, parseTeamBoxes(json)))
                .onErrorResume(e -> {
                    log.warn("ESPN 比赛概要拉取失败: event={}, error={}", game.eventId(), e.getMessage());
                    return Mono.just(new EspnGameDetail(game, List.of()));
                });
    }

    /**
     * 对阵匹配键（美东比赛日 + 主客队缩写，库内比赛与 ESPN 比赛两侧共用同一键）
     */
    public static String matchKey(LocalDate day, String awayTeam, String homeTeam) {
        return day.format(DATE_FORMATTER) + "|" + awayTeam + "|" + homeTeam;
    }

    /**
     * 解析 ESPN 计分板比赛列表（解析失败按空列表处理）
     */
    private List<EspnGame> parseGames(String json) {
        JSONArray events = Optional.ofNullable(JSON.parseObject(json))
                .map(o -> o.getJSONArray("events"))
                .orElse(null);
        if (events == null) {
            return List.of();
        }
        List<EspnGame> games = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            EspnGame game = parseGame(events.getJSONObject(i));
            if (game != null) {
                games.add(game);
            }
        }
        return games;
    }

    /**
     * 解析单场比赛：比赛日取开赛时刻的美东日期（ESPN 的 dates 参数即按此归档，与库内 day 同为美东比赛日）；
     * 缺少主客队或比赛日的异常条目跳过
     */
    private EspnGame parseGame(JSONObject event) {
        String date = StringUtils.trimToEmpty(event.getString("date"));
        JSONObject competition = Optional.ofNullable(event.getJSONArray("competitions"))
                .filter(list -> !list.isEmpty())
                .map(list -> list.getJSONObject(0))
                .orElse(null);
        if (competition == null || date.isEmpty()) {
            return null;
        }
        String awayTeam = null;
        String homeTeam = null;
        String awayScore = null;
        String homeScore = null;
        List<Integer> awayPeriods = List.of();
        List<Integer> homePeriods = List.of();
        JSONArray competitors = competition.getJSONArray("competitors");
        if (competitors != null) {
            for (int i = 0; i < competitors.size(); i++) {
                JSONObject competitor = competitors.getJSONObject(i);
                String abbr = Optional.ofNullable(competitor.getJSONObject("team"))
                        .map(team -> team.getString("abbreviation"))
                        .orElse(null);
                if (StringUtils.isBlank(abbr)) {
                    continue;
                }
                String team = ESPN_TO_DB_TEAM.getOrDefault(abbr, abbr);
                if ("away".equals(competitor.getString("homeAway"))) {
                    awayTeam = team;
                    awayScore = competitor.getString("score");
                    awayPeriods = parsePeriods(competitor);
                } else if ("home".equals(competitor.getString("homeAway"))) {
                    homeTeam = team;
                    homeScore = competitor.getString("score");
                    homePeriods = parsePeriods(competitor);
                }
            }
        }
        LocalDate day = parseDay(date);
        if (awayTeam == null || homeTeam == null || day == null) {
            return null;
        }
        JSONObject type = Optional.ofNullable(event.getJSONObject("status"))
                .map(status -> status.getJSONObject("type"))
                .orElse(null);
        String state = type == null ? "" : StringUtils.trimToEmpty(type.getString("state"));
        String description = type == null ? "" : StringUtils.trimToEmpty(type.getString("description"));
        // ESPN 对未开赛比赛返回 "0" 比分，须保留 NULL 语义（写出 0 会被页面显示为 0:0）
        boolean scoreAvailable = ("in".equals(state) || "post".equals(state))
                && !NO_SCORE_STATUSES.contains(description);
        return new EspnGame(matchKey(day, awayTeam, homeTeam),
                scoreAvailable ? parseScore(awayScore) : null,
                scoreAvailable ? parseScore(homeScore) : null,
                mapStatus(state, description),
                type != null && type.getBooleanValue("completed"),
                StringUtils.trimToEmpty(event.getString("id")),
                awayPeriods,
                homePeriods);
    }

    /**
     * 解析单队每节比分：按 linescores 的 period 序号定位（加时赛 period 递增），
     * 缺项补 0；未开赛等无 linescores 的情况返回空列表
     */
    private static List<Integer> parsePeriods(JSONObject competitor) {
        JSONArray linescores = competitor.getJSONArray("linescores");
        if (linescores == null || linescores.isEmpty()) {
            return List.of();
        }
        int maxPeriod = 0;
        for (int i = 0; i < linescores.size(); i++) {
            maxPeriod = Math.max(maxPeriod, linescores.getJSONObject(i).getIntValue("period"));
        }
        if (maxPeriod <= 0) {
            return List.of();
        }
        List<Integer> periods = new ArrayList<>(Collections.nCopies(maxPeriod, 0));
        for (int i = 0; i < linescores.size(); i++) {
            JSONObject line = linescores.getJSONObject(i);
            int period = line.getIntValue("period");
            Double value = line.getDouble("value");
            if (period >= 1 && period <= maxPeriod && value != null) {
                periods.set(period - 1, (int) Math.round(value));
            }
        }
        return periods;
    }

    /**
     * 解析概要接口的球员技术统计（boxscore.players 每队一项：表头 labels + 球员行 stats 按位对齐；
     * 队名缩写归一为库内体系；解析失败按空列表处理）
     */
    private List<EspnTeamBox> parseTeamBoxes(String json) {
        JSONArray players = Optional.ofNullable(JSON.parseObject(json))
                .map(o -> o.getJSONObject("boxscore"))
                .map(boxscore -> boxscore.getJSONArray("players"))
                .orElse(null);
        if (players == null) {
            return List.of();
        }
        List<EspnTeamBox> boxes = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            JSONObject block = players.getJSONObject(i);
            String abbr = Optional.ofNullable(block.getJSONObject("team"))
                    .map(team -> team.getString("abbreviation"))
                    .orElse(null);
            JSONObject statistics = Optional.ofNullable(block.getJSONArray("statistics"))
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.getJSONObject(0))
                    .orElse(null);
            if (StringUtils.isBlank(abbr) || statistics == null) {
                continue;
            }
            boxes.add(new EspnTeamBox(ESPN_TO_DB_TEAM.getOrDefault(abbr, abbr),
                    parseColumns(statistics), parsePlayerLines(statistics)));
        }
        return boxes;
    }

    private static List<String> parseColumns(JSONObject statistics) {
        JSONArray labels = Optional.ofNullable(statistics.getJSONArray("labels"))
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> statistics.getJSONArray("names"));
        if (labels == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            columns.add(StringUtils.trimToEmpty(labels.getString(i)));
        }
        return columns;
    }

    private static List<EspnPlayerLine> parsePlayerLines(JSONObject statistics) {
        JSONArray athletes = statistics.getJSONArray("athletes");
        if (athletes == null) {
            return List.of();
        }
        List<EspnPlayerLine> lines = new ArrayList<>(athletes.size());
        for (int i = 0; i < athletes.size(); i++) {
            JSONObject athlete = athletes.getJSONObject(i);
            String name = Optional.ofNullable(athlete.getJSONObject("athlete"))
                    .map(a -> a.getString("displayName"))
                    .orElse(null);
            if (StringUtils.isBlank(name)) {
                continue;
            }
            List<String> stats = new ArrayList<>();
            JSONArray values = athlete.getJSONArray("stats");
            if (values != null) {
                for (int j = 0; j < values.size(); j++) {
                    stats.add(StringUtils.trimToEmpty(values.getString(j)));
                }
            }
            lines.add(new EspnPlayerLine(name,
                    athlete.getBooleanValue("starter"),
                    !athlete.getBooleanValue("didNotPlay"),
                    stats));
        }
        return lines;
    }

    /**
     * ESPN 日期为 ISO UTC（秒可省略，如 2026-10-14T23:30Z，Instant.parse 不兼容无秒格式），
     * 转美东日期后作为比赛日
     */
    private static LocalDate parseDay(String date) {
        try {
            return OffsetDateTime.parse(date).atZoneSameInstant(GAME_ZONE).toLocalDate();
        } catch (DateTimeParseException e) {
            log.warn("ESPN 比赛日期解析失败: {}", date);
            return null;
        }
    }

    private static Integer parseScore(String score) {
        if (!StringUtils.isNumeric(score)) {
            return null;
        }
        return Integer.valueOf(score);
    }

    /**
     * ESPN 状态映射为库内状态词（与 SportsData 词表对齐：Scheduled/InProgress/Final/F/OT），
     * 非常规状态（Postponed/Canceled/Delayed 等）直译为描述文本
     */
    private static String mapStatus(String state, String description) {
        if ("in".equals(state)) {
            return "InProgress";
        }
        if ("post".equals(state)) {
            if (description.startsWith("Final/")) {
                return "F/" + description.substring("Final/".length());
            }
            return description.isEmpty() ? "Final" : description;
        }
        if (description.isEmpty() || "Scheduled".equals(description)) {
            return "Scheduled";
        }
        return description;
    }

    /**
     * ESPN 单场比赛（matchKey 为「美东比赛日|客队缩写|主队缩写」，缩写已归一为库内体系；
     * 未开赛比分保留 null，避免写出 0 破坏库内 NULL 语义；periods 为每节得分，无数据为空列表）
     */
    public record EspnGame(String matchKey, Integer awayScore, Integer homeScore, String status, boolean closed,
                           String eventId, List<Integer> awayPeriods, List<Integer> homePeriods) {
    }

    /**
     * ESPN 单场比赛详情（game 为计分板条目，boxes 为双方球员数据表，概要拉取失败时为空列表）
     */
    public record EspnGameDetail(EspnGame game, List<EspnTeamBox> boxes) {
    }

    /**
     * 单队球员数据表（columns 为表头，players 行内 stats 与表头按位对齐）
     */
    public record EspnTeamBox(String team, List<String> columns, List<EspnPlayerLine> players) {
    }

    /**
     * 球员单场数据行（played 为未 DNP；stats 为原始字符串，如 "4-8" 命中率格式）
     */
    public record EspnPlayerLine(String name, boolean starter, boolean played, List<String> stats) {
    }
}

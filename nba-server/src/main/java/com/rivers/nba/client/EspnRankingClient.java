package com.rivers.nba.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.client.WebClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>
 * ESPN 排行榜客户端：球队战绩取 standings 接口（按东西部归档），球员赛季统计取 byathlete 接口。
 * ESPN 不提供赛区（division），赛区与球队主数据由库内 team 表补齐；
 * 球队缩写经 EspnScoreboardClient 的统一映射归一为库内体系，保证与比赛接口口径一致
 * </p>
 *
 * @author rivers
 * @since 2026-10-24
 */
@Slf4j
@Component
public class EspnRankingClient {

    private static final String STANDINGS_URL =
            "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings?season=";

    private static final String ATHLETE_STATS_URL =
            "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/statistics/byathlete"
                    + "?region=us&lang=en&contentorigin=espn&isqualified=false";

    /**
     * ESPN 单页上限内可覆盖全联盟球员（约 350 人），避免多页拉取
     */
    private static final int ATHLETE_PAGE_SIZE = 500;

    private final WebClientUtil webClientUtil;

    public EspnRankingClient(WebClientUtil webClientUtil) {
        this.webClientUtil = webClientUtil;
    }

    /**
     * 拉取球队战绩（season 为结束年份，2027 = 2026-27 赛季；失败按空列表处理）
     */
    public Mono<List<EspnTeamStanding>> fetchStandings(int season) {
        return webClientUtil.get(STANDINGS_URL + season)
                .map(this::parseStandings)
                .onErrorResume(e -> {
                    log.warn("ESPN 球队战绩拉取失败: season={}, error={}", season, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    /**
     * 拉取球员赛季统计（默认常规赛，失败按空列表处理）
     */
    public Mono<List<EspnPlayerStat>> fetchPlayerStats(int season) {
        return fetchPlayerStats(season, 2);
    }

    /**
     * 拉取球员赛季统计（seasonType：1 季前赛 2 常规赛 3 季后赛；季前赛 byathlete 不提供数据）
     */
    public Mono<List<EspnPlayerStat>> fetchPlayerStats(int season, int seasonType) {
        return fetchAthletePage(season, seasonType, 1)
                .flatMap(first -> first.pages() <= 1
                        ? Mono.just(first.rows())
                        : appendRemainingPages(season, seasonType, first));
    }

    private Mono<List<EspnPlayerStat>> appendRemainingPages(int season, int seasonType, AthletePage first) {
        return Flux.range(2, first.pages() - 1)
                .concatMap(page -> fetchAthletePage(season, seasonType, page))
                .map(AthletePage::rows)
                .collectList()
                .map(rest -> {
                    List<EspnPlayerStat> all = new ArrayList<>(first.rows());
                    rest.forEach(all::addAll);
                    return all;
                });
    }

    private Mono<AthletePage> fetchAthletePage(int season, int seasonType, int page) {
        String url = ATHLETE_STATS_URL + "&seasontype=" + seasonType
                + "&season=" + season + "&limit=" + ATHLETE_PAGE_SIZE + "&page=" + page;
        return webClientUtil.get(url)
                .map(json -> parseAthletePage(json, season))
                .onErrorResume(e -> {
                    log.warn("ESPN 球员统计拉取失败: season={}, seasonType={}, page={}, error={}",
                            season, seasonType, page, e.getMessage());
                    return Mono.just(new AthletePage(List.of(), 1));
                });
    }

    private List<EspnTeamStanding> parseStandings(String json) {
        JSONObject root = JSON.parseObject(json);
        JSONArray conferences = root == null ? null : root.getJSONArray("children");
        if (conferences == null) {
            return List.of();
        }
        List<EspnTeamStanding> rows = new ArrayList<>(30);
        for (int i = 0; i < conferences.size(); i++) {
            JSONArray entries = Optional.ofNullable(conferences.getJSONObject(i).getJSONObject("standings"))
                    .map(standings -> standings.getJSONArray("entries"))
                    .orElse(null);
            if (entries == null) {
                continue;
            }
            for (int j = 0; j < entries.size(); j++) {
                EspnTeamStanding row = parseStanding(entries.getJSONObject(j));
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private EspnTeamStanding parseStanding(JSONObject entry) {
        String abbr = Optional.ofNullable(entry.getJSONObject("team"))
                .map(team -> team.getString("abbreviation"))
                .orElse(null);
        JSONArray stats = entry.getJSONArray("stats");
        if (StringUtils.isBlank(abbr) || stats == null) {
            return null;
        }
        Map<String, JSONObject> byName = new HashMap<>();
        for (int i = 0; i < stats.size(); i++) {
            JSONObject stat = stats.getJSONObject(i);
            byName.put(stat.getString("name"), stat);
        }
        int[] lastTen = parseRecord(text(byName, "Last Ten Games"));
        return new EspnTeamStanding(EspnScoreboardClient.toDbTeam(abbr),
                intValue(byName, "wins"),
                intValue(byName, "losses"),
                doubleValue(byName, "gamesBehind"),
                streakText(byName),
                lastTen[0],
                lastTen[1],
                doubleValue(byName, "avgPointsFor"),
                doubleValue(byName, "avgPointsAgainst"));
    }

    /**
     * byathlete 返回按类别（general/offensive/defensive）分组的定长数组，
     * 表头由顶层 categories 的 names 定义，球员 categories 的 values 按位对齐
     */
    private AthletePage parseAthletePage(String json, int season) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) {
            return new AthletePage(List.of(), 1);
        }
        Map<String, List<String>> headers = parseHeaders(root.getJSONArray("categories"));
        JSONArray athletes = root.getJSONArray("athletes");
        int pages = Optional.ofNullable(root.getJSONObject("pagination"))
                .map(pagination -> pagination.getIntValue("pages"))
                .orElse(1);
        if (athletes == null) {
            return new AthletePage(List.of(), Math.max(pages, 1));
        }
        List<EspnPlayerStat> rows = new ArrayList<>(athletes.size());
        for (int i = 0; i < athletes.size(); i++) {
            EspnPlayerStat row = parseAthlete(athletes.getJSONObject(i), headers);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty() && season > 0) {
            log.debug("ESPN 球员统计为空: season={}", season);
        }
        return new AthletePage(rows, Math.max(pages, 1));
    }

    private static Map<String, List<String>> parseHeaders(JSONArray categories) {
        Map<String, List<String>> headers = new HashMap<>();
        if (categories == null) {
            return headers;
        }
        for (int i = 0; i < categories.size(); i++) {
            JSONObject category = categories.getJSONObject(i);
            JSONArray names = category.getJSONArray("names");
            if (names == null) {
                continue;
            }
            List<String> list = new ArrayList<>(names.size());
            for (int j = 0; j < names.size(); j++) {
                list.add(names.getString(j));
            }
            headers.put(category.getString("name"), list);
        }
        return headers;
    }

    private EspnPlayerStat parseAthlete(JSONObject entry, Map<String, List<String>> headers) {
        JSONObject athlete = entry.getJSONObject("athlete");
        if (athlete == null) {
            return null;
        }
        Map<String, Double> values = parseValues(entry.getJSONArray("categories"), headers);
        int games = values.getOrDefault("gamesPlayed", 0.0).intValue();
        if (games <= 0) {
            return null;
        }
        String abbr = StringUtils.trimToEmpty(athlete.getString("teamShortName"));
        return new EspnPlayerStat(new EspnAthlete(athlete.getLongValue("id"),
                StringUtils.trimToEmpty(athlete.getString("displayName")),
                StringUtils.trimToEmpty(athlete.getString("firstName")),
                StringUtils.trimToEmpty(athlete.getString("lastName"))),
                EspnScoreboardClient.toDbTeam(abbr),
                Optional.ofNullable(athlete.getJSONObject("position"))
                        .map(position -> StringUtils.trimToEmpty(position.getString("abbreviation")))
                        .orElse(""),
                games,
                values.getOrDefault("points", 0.0),
                values.getOrDefault("rebounds", 0.0),
                values.getOrDefault("assists", 0.0),
                values.getOrDefault("steals", 0.0),
                values.getOrDefault("blocks", 0.0),
                values.getOrDefault("threePointFieldGoalsMade", 0.0),
                values.getOrDefault("threePointFieldGoalsAttempted", 0.0),
                values.getOrDefault("fieldGoalsMade", 0.0),
                values.getOrDefault("fieldGoalsAttempted", 0.0),
                values.getOrDefault("freeThrowsMade", 0.0),
                values.getOrDefault("freeThrowsAttempted", 0.0),
                values.getOrDefault("minutes", 0.0),
                values.getOrDefault("fouls", 0.0));
    }

    /**
     * 按类别表头展开统计值（同类统计取总量字段，如 points/rebounds/assists）
     */
    private static Map<String, Double> parseValues(JSONArray categories, Map<String, List<String>> headers) {
        Map<String, Double> values = new HashMap<>();
        if (categories == null) {
            return values;
        }
        for (int i = 0; i < categories.size(); i++) {
            JSONObject category = categories.getJSONObject(i);
            String name = category.getString("name");
            JSONArray raw = category.getJSONArray("values");
            List<String> names = headers.get(name);
            if (raw == null || names == null) {
                continue;
            }
            int size = Math.min(raw.size(), names.size());
            for (int j = 0; j < size; j++) {
                Double value = raw.getDouble(j);
                if (value != null) {
                    values.put(names.get(j), value);
                }
            }
        }
        return values;
    }

    private static int intValue(Map<String, JSONObject> stats, String name) {
        return Optional.ofNullable(stats.get(name))
                .map(stat -> stat.getIntValue("value"))
                .orElse(0);
    }

    private static double doubleValue(Map<String, JSONObject> stats, String name) {
        return Optional.ofNullable(stats.get(name))
                .map(stat -> stat.getDoubleValue("value"))
                .orElse(0.0);
    }

    private static String text(Map<String, JSONObject> stats, String name) {
        return Optional.ofNullable(stats.get(name))
                .map(stat -> StringUtils.trimToEmpty(stat.getString("displayValue")))
                .orElse("");
    }

    /**
     * 连胜/连败（displayValue 如 W1/L2），无比赛时为 "-" 按空串处理
     */
    private static String streakText(Map<String, JSONObject> stats) {
        String streak = text(stats, "streak");
        return "-".equals(streak) ? "" : streak;
    }

    /**
     * 近十场战绩（displayValue 如 0-2），无数据时返回 0/0
     */
    private static int[] parseRecord(String record) {
        String[] parts = record.split("-");
        if (parts.length != 2 || !StringUtils.isNumeric(parts[0]) || !StringUtils.isNumeric(parts[1])) {
            return new int[]{0, 0};
        }
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /**
     * ESPN 球队战绩（teamKey 已归一为库内缩写）
     */
    public record EspnTeamStanding(String teamKey, int wins, int losses, double gamesBehind, String streak,
                                   int lastTenWins, int lastTenLosses,
                                   double pointsPerGameFor, double pointsPerGameAgainst) {
    }

    /**
     * ESPN 球员赛季统计（均为赛季总量，场均由服务层按 games 计算）
     */
    public record EspnPlayerStat(EspnAthlete athlete, String team, String position, int games,
                                 double points, double rebounds, double assists, double steals,
                                 double blocks, double threePointersMade, double threePointersAttempted,
                                 double fieldGoalsMade, double fieldGoalsAttempted, double freeThrowsMade,
                                 double freeThrowsAttempted, double minutes, double fouls) {
    }

    /**
     * ESPN 球员身份（ESPN 球员 ID 与库内 SportsData 体系不同源，服务层按姓名映射）
     */
    public record EspnAthlete(long espnId, String name, String firstName, String lastName) {
    }

    private record AthletePage(List<EspnPlayerStat> rows, int pages) {
    }
}

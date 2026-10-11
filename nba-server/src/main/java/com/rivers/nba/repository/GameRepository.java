package com.rivers.nba.repository;

import com.rivers.nba.entity.Game;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * <p>
 * 比赛信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface GameRepository extends ReactiveCrudRepository<Game, Long> {

    @Query("""
            SELECT * FROM game WHERE is_deleted = 0
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            AND (:season = 0 OR season = :season)
            AND (:date = '' OR DATE_FORMAT(day, '%Y-%m-%d') = :date)
            ORDER BY day ASC, id ASC LIMIT :limit OFFSET :offset
            """)
    Flux<Game> pageByFilter(int teamId, int season, String date, int limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM game WHERE is_deleted = 0
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            AND (:season = 0 OR season = :season)
            AND (:date = '' OR DATE_FORMAT(day, '%Y-%m-%d') = :date)
            """)
    Mono<Long> countByFilter(int teamId, int season, String date);

    /**
     * 从当天起的最近几场比赛（day 列仅存比赛日零点，须用 CURDATE() 比较；
     * 若用 NOW() 比较会把当天凌晨零点的记录整体排除，导致首页“最近的比赛”跳过当天）
     */
    @Query("SELECT * FROM game WHERE is_deleted = 0 AND day >= CURDATE() ORDER BY day ASC, id ASC LIMIT :limit")
    Flux<Game> findUpcoming(int limit);

    @Query("SELECT * FROM game WHERE is_deleted = 0 AND game_id = :gameId LIMIT 1")
    Mono<Game> findByGameId(int gameId);

    @Query("""
            SELECT * FROM game WHERE is_deleted = 0 AND is_closed = 0
            AND DATE(day) = :date
            ORDER BY day ASC, id ASC
            """)
    Flux<Game> findActiveByDay(String date);

    @Modifying
    @Query("""
            UPDATE game SET away_team_score = :awayTeamScore, home_team_score = :homeTeamScore,
            status = :status, is_closed = :isClosed, updated = :updated, update_user = :updateUser
            WHERE is_deleted = 0 AND game_id = :gameId
            """)
    Mono<Long> updateScore(int gameId, int awayTeamScore, int homeTeamScore, String status, boolean isClosed,
                           LocalDateTime updated, String updateUser);

    @Query("""
            SELECT * FROM game WHERE is_deleted = 0 AND DATE_FORMAT(day, '%Y-%m') = :month
            AND (:season = 0 OR season = :season)
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            ORDER BY day ASC, id ASC
            """)
    Flux<Game> findByMonth(String month, int season, int teamId);

    @Modifying
    @Query("""
            UPDATE game SET season = :season, season_type = :seasonType, status = :status,
            day = :day, date_time = :dateTime, away_team = :awayTeam, home_team = :homeTeam,
            away_team_id = :awayTeamId, home_team_id = :homeTeamId, stadium_id = :stadiumId,
            channel = :channel, attendance = :attendance, away_team_score = :awayTeamScore,
            home_team_score = :homeTeamScore, updated = :updated, is_closed = :isClosed,
            game_end_date_time = :gameEndDateTime, neutral_venue = :neutralVenue,
            date_time_utc = :dateTimeUtc, inseason_tournament = :inseasonTournament,
            global_game_id = :globalGameId, update_user = :updateUser
            WHERE is_deleted = 0 AND game_id = :gameId
            """)
    Mono<Long> updateGame(int gameId, int season, int seasonType, String status, LocalDateTime day,
                          LocalDateTime dateTime, String awayTeam, String homeTeam, int awayTeamId, int homeTeamId,
                          int stadiumId, String channel, Integer attendance, Integer awayTeamScore,
                          Integer homeTeamScore, LocalDateTime updated, boolean isClosed,
                          LocalDateTime gameEndDateTime, boolean neutralVenue, LocalDateTime dateTimeUtc,
                          boolean inseasonTournament, int globalGameId, String updateUser);
}

package com.rivers.batch.service.impl;

import com.google.common.collect.Lists;
import com.rivers.batch.service.IJobMonitorService;
import com.rivers.batch.vo.StatusCountVO;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Scheduler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobMonitorServiceImpl implements IJobMonitorService {

    private final JobRepository jobRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Scheduler scheduler;

    public JobMonitorServiceImpl(JobRepository jobRepository, JdbcTemplate jdbcTemplate, Scheduler scheduler) {
        this.jobRepository = jobRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.scheduler = scheduler;
    }

    @Override
    public Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts() {
        int pageSize = 1000;
        int offset = 0;
        var ref = new Object() {
            long total = 0;
        };
        String sql = "SELECT STATUS, COUNT(*) AS count FROM BATCH_JOB_EXECUTION GROUP BY STATUS LIMIT :limit OFFSET :offset";
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        List<StatusCountVO> allStatusCounts = Lists.newArrayList();
        // 使用RowMapper映射到自定义对象
        boolean shouldContinue = true;
        while (shouldContinue) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("limit", pageSize);
            params.addValue("offset", offset);
            List<StatusCountVO> pageResults = namedJdbcTemplate.query(sql, params, new StatusCountRowMapper());
            if (!pageResults.isEmpty()) {
                allStatusCounts.addAll(pageResults);
                ref.total += pageResults.stream().mapToLong(StatusCountVO::getCount).sum();
                if (pageResults.size() == pageSize) {
                    offset += pageSize;
                } else {
                    shouldContinue = false;
                }
            } else {
                shouldContinue = false;
            }
        }
        List<JobExecutionCount> list = allStatusCounts.stream()
                .map(i ->
                        JobExecutionCount.newBuilder()
                                .setStatus(i.getStatus())
                                .setCount(i.getCount())
                                .setRate(String.valueOf(BigDecimal.valueOf(i.getCount())
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(BigDecimal.valueOf(ref.total), 2, RoundingMode.HALF_UP)))
                                .build())
                .toList();
        // 5. 构建响应
        JobExecutionRes response = JobExecutionRes.newBuilder()
                .addAllJobExecutionCounts(list)
                .setTotalCount(ref.total)
                .build();
        return Mono.just(ResultVO.ok(response));
    }

    @SneakyThrows
    @Override
    public Mono<ResultVO<SchedulesRes>> getSchedules() {
        SchedulerStatusRes schedulerStatusRes = SchedulerStatusRes.newBuilder()
                .setSchedulerName(scheduler.getSchedulerName())
                .setStatus(scheduler.isStarted() ? "STARTED" : "STOPPED")
                .build();
        return Mono.just(ResultVO.ok(SchedulesRes.newBuilder()
                .addAllSchedulerStatusRes(Lists.newArrayList(schedulerStatusRes))
                .build()));
    }

    @Override
    public Mono<ResultVO<JobDateExecutionsRes>> getJobExecutionByDate(JobDateExecutionReq jobExecutionReq) {
        String time = jobExecutionReq.getTime();
        if (StringUtils.isBlank(time)) {
            return Mono.just(ResultVO.fail("时间不能为空"));
        }
        String sql = "SELECT CREATE_TIME, STATUS, COUNT(*) AS count FROM BATCH_JOB_EXECUTION " +
                "WHERE CREATE_TIME >= :startTime AND CREATE_TIME <= :endTime " +
                "GROUP BY STATUS, CREATE_TIME";
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if ("7".equals(time)) {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(6);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            params.addValue("startTime", startDate.format(formatter) + " 00:00:00");
            params.addValue("endTime", endDate.format(formatter) + " 23:59:59");
        } else {
            params.addValue("startTime", time + " 00:00:00");
            params.addValue("endTime", time + " 23:59:59");
        }
        List<StatusCountVO> statusCounts = namedJdbcTemplate.query(sql, params, new StatusCountRowMapper());
        Map<String, List<StatusCountVO>> statusMap = statusCounts.stream()
                .collect(Collectors.groupingBy(StatusCountVO::getStatus));
        return Mono.just(ResultVO.ok(JobExecutionRes.newBuilder().addAllJobExecutionCounts(list).build()));
    }

    // RowMapper实现
    private static class StatusCountRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString("STATUS"),
                    rs.getInt("count"),
                    rs.getTimestamp("CREATE_TIME").toLocalDateTime()
            );
        }
    }
}
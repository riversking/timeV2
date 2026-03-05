package com.rivers.batch.service.impl;

import cn.hutool.core.util.NumberUtil;
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
    public ResultVO<JobExecutionRes> getJobExecutionCounts() {
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
        return ResultVO.ok(response);
    }

    @SneakyThrows
    @Override
    public ResultVO<SchedulesRes> getSchedules() {
        SchedulerStatusRes schedulerStatusRes = SchedulerStatusRes.newBuilder()
                .setSchedulerName(scheduler.getSchedulerName())
                .setStatus(scheduler.isStarted() ? "STARTED" : "STOPPED")
                .build();
        return ResultVO.ok(SchedulesRes.newBuilder()
                .addAllSchedulerStatusRes(Lists.newArrayList(schedulerStatusRes))
                .build());
    }

    @Override
    public ResultVO<JobDateExecutionsRes> getJobExecutionByDate(JobDateExecutionReq jobExecutionReq) {
        String time = jobExecutionReq.getTime();
        if (StringUtils.isBlank(time)) {
            return ResultVO.fail("时间不能为空");
        }
        if (!NumberUtil.isNumber(time)) {
            return ResultVO.fail("时间格式错误");
        }
        String sql = "SELECT CREATE_TIME, STATUS, COUNT(*) AS count FROM BATCH_JOB_EXECUTION " +
                "WHERE CREATE_TIME >= :startTime AND CREATE_TIME <= :endTime " +
                "GROUP BY STATUS, CREATE_TIME";
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource params = new MapSqlParameterSource();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Long.parseLong(time));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        params.addValue("startTime", startDate.format(formatter) + " 00:00:00");
        params.addValue("endTime", endDate.format(formatter) + " 23:59:59");
        List<StatusCountVO> statusCounts = namedJdbcTemplate.query(sql, params, new StatusWithTimeRowMapper());
        Map<String, Map<String, Integer>> dateStatusMap = statusCounts.stream()
                .collect(Collectors.groupingBy(k ->
                                k.getCreateTime().getMonthValue() + "-" + k.getCreateTime().getDayOfMonth(),
                        Collectors.groupingBy(StatusCountVO::getStatus,
                                Collectors.summingInt(StatusCountVO::getCount))));
        List<JobDateExecutionRes> list = dateStatusMap.entrySet().stream()
                .map(i ->
                        JobDateExecutionRes.newBuilder()
                                .setSuccessCount(String.valueOf(i.getValue().getOrDefault("COMPLETED", 0)))
                                .setFailureCount(String.valueOf(i.getValue().getOrDefault("FAILED", 0)))
                                .setMonthDay(i.getKey())
                                .build())
                .toList();
        return ResultVO.ok(JobDateExecutionsRes.newBuilder().addAllJobDateExecutions(list).build());
    }

    // RowMapper实现
    private static class StatusCountRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString("STATUS"),
                    rs.getInt("count"),
                    null
            );
        }
    }

    private static class StatusWithTimeRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString("STATUS"),
                    rs.getInt("count"),
                    rs.getTimestamp("CREATE_TIME") != null ? rs.getTimestamp("CREATE_TIME").toLocalDateTime() : null
            );
        }
    }
}
package com.rivers.batch.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.rivers.batch.entity.QrtzTriggers;
import com.rivers.batch.mapper.QrtzTriggersMapper;
import com.rivers.batch.service.IJobMonitorService;
import com.rivers.batch.vo.JobCountVO;
import com.rivers.batch.vo.StatusCountVO;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

import static com.google.common.collect.Maps.newHashMap;

/**
 * @author xx
 */
@Service
public class JobMonitorServiceImpl implements IJobMonitorService {

    public static final String COUNT = "count";
    public static final String STATUS = "STATUS";
    private final JdbcTemplate jdbcTemplate;

    private final QrtzTriggersMapper qrtzTriggersMapper;

    public JobMonitorServiceImpl(JdbcTemplate jdbcTemplate, QrtzTriggersMapper qrtzTriggersMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.qrtzTriggersMapper = qrtzTriggersMapper;
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
        List<JobExecutionCountRes> list = allStatusCounts.stream()
                .map(i ->
                        JobExecutionCountRes.newBuilder()
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
        List<QrtzTriggers> qrtzTriggers = qrtzTriggersMapper.selectList(Wrappers.emptyWrapper());
        if (CollectionUtils.isEmpty(qrtzTriggers)) {
            return ResultVO.fail("没有任务");
        }
        String sql = "SELECT B.JOB_NAME,A.STATUS, COUNT(*) AS count FROM BATCH_JOB_EXECUTION A " +
                "LEFT JOIN BATCH_JOB_INSTANCE B ON A.JOB_INSTANCE_ID = B.JOB_INSTANCE_ID " +
                "WHERE B.JOB_NAME IN (:jobName) GROUP BY B.JOB_NAME, A.STATUS";
        List<String> jobNames = qrtzTriggers.stream()
                .map(QrtzTriggers::getJobName)
                .toList();
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobName", jobNames);
        List<JobCountVO> jobCounts = namedJdbcTemplate.query(sql, params, new JobCountRowMapper());
        Map<String, Map<String, Long>> jobStatusCountMap = jobCounts.stream()
                .collect(Collectors.groupingBy(JobCountVO::getJobName,
                        Collectors.groupingBy(JobCountVO::getStatus, Collectors.summingLong(JobCountVO::getCount))));
        List<SchedulerStatusRes> list = qrtzTriggers.stream()
                .map(i -> {
                    String jobName = i.getJobName();
                    Runtime runtime = Runtime.getRuntime();
                    long maxMemory = runtime.maxMemory();
                    long totalMemory = runtime.totalMemory();
                    long freeMemory = runtime.freeMemory();
                    long usedMemory = totalMemory - freeMemory;
                    int cpuCores = runtime.availableProcessors();
                    Map<String, Long> statusCount = jobStatusCountMap.getOrDefault(jobName, newHashMap());
                    long totalSum = statusCount.values().stream().mapToLong(Long::longValue).sum();
                    Long successCount = statusCount.getOrDefault("COMPLETED", 0L);
                    return SchedulerStatusRes.newBuilder()
                            .setSchedulerName(i.getTriggerName())
                            .setStatus(i.getTriggerState())
                            .setMaxMemory(String.valueOf(maxMemory))
                            .setTotalMemory(String.valueOf(totalMemory))
                            .setFreeMemory(String.valueOf(freeMemory))
                            .setUsedMemory(String.valueOf(totalSum > 0 ? BigDecimal.valueOf(usedMemory)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(BigDecimal.valueOf(maxMemory), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO))
                            .setCpuCores(String.valueOf(cpuCores))
                            .setJobCount(String.valueOf(totalSum))
                            .setSuccessFullyCount(String.valueOf(totalSum > 0 ? BigDecimal.valueOf(successCount)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(BigDecimal.valueOf(totalSum), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO))
                            .build();
                })
                .toList();
        return ResultVO.ok(SchedulesRes.newBuilder()
                .addAllSchedules(list)
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
        List<String> allDates = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> date.getMonthValue() + "-" + date.getDayOfMonth())
                .toList();
        List<JobDateExecutionRes> list = allDates.stream()
                .map(date -> {
                    Map<String, Integer> statusMap = dateStatusMap.getOrDefault(date, newHashMap());
                    Integer completed = statusMap.getOrDefault("COMPLETED", 0);
                    Integer failed = statusMap.getOrDefault("FAILED", 0);
                    int sum = completed + failed;
                    BigDecimal successPercent = sum > 0
                            ? BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(sum), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    BigDecimal failurePercent = sum > 0
                            ? BigDecimal.valueOf(failed).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(sum), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return JobDateExecutionRes.newBuilder()
                            .setSuccessCount(String.valueOf(completed))
                            .setFailureCount(String.valueOf(failed))
                            .setSuccessPercent(String.valueOf(successPercent))
                            .setFailurePercent(String.valueOf(failurePercent))
                            .setMonthDay(date)
                            .build();
                })
                .toList();
        return ResultVO.ok(JobDateExecutionsRes.newBuilder().addAllJobDateExecutions(list).build());
    }

    // RowMapper实现
    private static class StatusCountRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString(STATUS),
                    rs.getInt(COUNT),
                    null
            );
        }
    }

    private static class StatusWithTimeRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString(STATUS),
                    rs.getInt(COUNT),
                    rs.getTimestamp("CREATE_TIME") != null ? rs.getTimestamp("CREATE_TIME").toLocalDateTime() : null
            );
        }
    }

    private static class JobCountRowMapper implements RowMapper<JobCountVO> {
        @Override
        public JobCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new JobCountVO(
                    rs.getLong(COUNT),
                    rs.getString("JOB_NAME"),
                    rs.getString(STATUS)
            );
        }
    }
}
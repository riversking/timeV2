package com.rivers.batch.service.impl;

import com.rivers.batch.service.IJobMonitorService;
import com.rivers.batch.vo.StatusCountVO;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionCount;
import com.rivers.proto.JobExecutionRes;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobMonitorServiceImpl implements IJobMonitorService {

    private final JobRepository jobRepository;
    private final JdbcTemplate jdbcTemplate;

    public JobMonitorServiceImpl(JobRepository jobRepository, JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts() {
        // 1. 获取所有 JobName
        List<String> jobNames = jobRepository.getJobNames();
        // 2. 批量获取所有 JobInstances
        List<JobInstance> allJobInstances = new ArrayList<>();
        for (String jobName : jobNames) {
            List<JobInstance> jobInstances = jobRepository.getJobInstances(jobName, 0, Integer.MAX_VALUE);
            allJobInstances.addAll(jobInstances);
        }
        // 3. 获取所有 JobInstance ID
        List<Long> jobInstanceIds = allJobInstances.stream()
                .map(JobInstance::getInstanceId)
                .toList();
        // 4. 关键优化：使用数据库聚合查询 + 自定义对象接收
        Map<String, Integer> statusCounts = new HashMap<>();
        if (!CollectionUtils.isEmpty(jobInstanceIds)) {
            String sql = "SELECT STATUS, COUNT(*) AS count FROM BATCH_JOB_EXECUTION " +
                    "WHERE JOB_INSTANCE_ID IN (:jobInstanceIds) " +
                    "GROUP BY STATUS";

            MapSqlParameterSource params = new MapSqlParameterSource("jobInstanceIds", jobInstanceIds);
            NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
            // 使用RowMapper映射到自定义对象
            List<StatusCountVO> statusCountsList = namedJdbcTemplate.query(
                    sql,
                    params,
                    new StatusCountRowMapper()
            );
            // 将结果转换为Map
            for (StatusCountVO statusCount : statusCountsList) {
                statusCounts.put(statusCount.getStatus(), statusCount.getCount());
            }
        }
        // 5. 构建响应
        JobExecutionRes response = JobExecutionRes.newBuilder()
                .addAllJobExecutionCounts(statusCounts.entrySet().stream()
                        .map(e -> JobExecutionCount.newBuilder()
                                .setStatus(e.getKey())
                                .setCount(e.getValue())
                                .build())
                        .toList())
                .build();
        return Mono.just(ResultVO.ok(response));
    }

    // RowMapper实现
    private static class StatusCountRowMapper implements RowMapper<StatusCountVO> {
        @Override
        public StatusCountVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StatusCountVO(
                    rs.getString("STATUS"),
                    rs.getInt("count")
            );
        }
    }
}
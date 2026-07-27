package com.rivers.batch.aspect;

import com.rivers.batch.entity.TaskExecuteLog;
import com.rivers.batch.service.TaskLogSaveService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
public class TaskLogAspect {

    private static final int MAX_ERROR_MSG_LEN = 2000;

    private final DiscoveryClient discoveryClient;
    private final TaskLogSaveService taskLogSaveService;
    private final String localIp;

    public TaskLogAspect(DiscoveryClient discoveryClient,
                         TaskLogSaveService taskLogSaveService,
                         InetUtils inetUtils) {
        this.discoveryClient = discoveryClient;
        this.taskLogSaveService = taskLogSaveService;
        this.localIp = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
    }

    @Around("@annotation(com.rivers.batch.annotation.TaskLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        var startTime = System.currentTimeMillis();
        var snapshot = extractParams(joinPoint);
        var success = true;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            errorMsg = truncate(e.getMessage());
            throw e;
        } finally {
            try {
                saveLog(snapshot, startTime, success, errorMsg);
            } catch (Exception e) {
                log.error("Failed to submit task execution log", e);
            }
        }
    }

    /** 从方法参数中提取 ChunkContext 中的 JobParameters 快照 */
    private static JobSnapshot extractParams(ProceedingJoinPoint joinPoint) {
        for (var arg : joinPoint.getArgs()) {
            if (arg instanceof ChunkContext chunkContext) {
                var params = chunkContext.getStepContext().getJobParameters();
                return new JobSnapshot(
                        (String) params.get("taskName"),
                        (String) params.get("serverName"),
                        (String) params.get("trigger"));
            }
        }
        return JobSnapshot.EMPTY;
    }

    /** 组装实体并走虚拟线程异步入库 */
    private void saveLog(JobSnapshot snapshot, long startTime,
                         boolean success, String errorMsg) {
        var entity = new TaskExecuteLog();
        entity.setTaskName(snapshot.taskName);
        entity.setServerName(snapshot.serverName);
        entity.setExecutorIp(localIp);
        entity.setTargetIp(resolveTargetIp(snapshot.serverName));
        entity.setExecuteTime(System.currentTimeMillis() - startTime);
        entity.setStatus(success ? "SUCCESS" : "FAILED");
        entity.setErrorMsg(errorMsg);
        entity.setTriggerType(snapshot.triggerType);
        taskLogSaveService.saveAsync(entity);
    }

    /** 通过 Nacos DiscoveryClient 解析目标服务实例 IP */
    private String resolveTargetIp(String serverName) {
        // 优先级最高：WebClient filter 截获的实际连接 host
        var actualHost = TargetIpHolder.get();
        if (actualHost != null && !actualHost.isBlank()) {
            return resolveToIp(actualHost);
        }
        // 降级：DiscoveryClient 拿全部实例
        if (serverName == null || serverName.isBlank()) {
            return "";
        }
        try {
            var instances = discoveryClient.getInstances(serverName);
            if (instances.isEmpty()) {
                return "";
            }
            return instances.stream()
                    .map(ServiceInstance::getHost)
                    .distinct()
                    .collect(Collectors.joining(","));
        } catch (Exception e) {
            log.warn("Failed to resolve target IP for service: {}", serverName, e);
            return "";
        }
    }

    private static String resolveToIp(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (UnknownHostException _) {
            return host;
        }
    }

    private static String truncate(String str) {
        if (str == null) {
            return null;
        }
        return str.length() <= MAX_ERROR_MSG_LEN
                ? str
                : str.substring(0, MAX_ERROR_MSG_LEN);
    }

    /** 从 ChunkContext 提取的参数快照，避免在 finally 中重复遍历 args */
    private record JobSnapshot(String taskName, String serverName, String triggerType) {
        static final JobSnapshot EMPTY = new JobSnapshot(null, null, null);
    }
}
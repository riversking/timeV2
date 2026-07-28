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
import org.springframework.cloud.client.loadbalancer.*;
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
    private final LoadBalancerClient loadBalancerClient;
    private final TaskLogSaveService taskLogSaveService;
    private final String localIp;

    public TaskLogAspect(DiscoveryClient discoveryClient, LoadBalancerClient loadBalancerClient,
                         TaskLogSaveService taskLogSaveService,
                         InetUtils inetUtils) {
        this.discoveryClient = discoveryClient;
        this.loadBalancerClient = loadBalancerClient;
        this.taskLogSaveService = taskLogSaveService;
        this.localIp = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
    }

    @Around("@annotation(com.rivers.batch.annotation.TaskLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        var startTime = System.currentTimeMillis();
        var snapshot = extractParams(joinPoint);
        // 同步解析目标 IP
        var targetIp = resolveTargetIpSync(snapshot.serverName);
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
                saveLog(snapshot, startTime, success, errorMsg, targetIp);
            } catch (Exception e) {
                log.error("Failed to submit task execution log", e);
            }
        }
    }

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

    private void saveLog(JobSnapshot snapshot, long startTime,
                         boolean success, String errorMsg, String targetIp) {
        var entity = new TaskExecuteLog();
        entity.setTaskName(snapshot.taskName);
        entity.setServerName(snapshot.serverName);
        entity.setExecutorIp(localIp);
        entity.setTargetIp(targetIp);
        entity.setExecuteTime(System.currentTimeMillis() - startTime);
        entity.setStatus(success ? "SUCCESS" : "FAILED");
        entity.setErrorMsg(errorMsg);
        entity.setTriggerType(snapshot.triggerType);
        taskLogSaveService.saveAsync(entity);
    }

    /**
     * 同步解析目标 IP——与 lb:// 走同一负载均衡器，全程在调用线程
     */
    private String resolveTargetIpSync(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return "";
        }
        // 主路径：LoadBalancerClient 同步选择（底层复用 lb:// 的 ReactorLoadBalancer）
        try {
            var instance = loadBalancerClient.choose(serverName);
            return resolveToIp(instance.getHost());
        } catch (Exception e) {
            log.warn("LoadBalancer failed for [{}], fallback", serverName, e);
        }
        // 降级：DiscoveryClient 全量列表
        var instances = discoveryClient.getInstances(serverName);
        if (instances.isEmpty()) {
            return "";
        }
        return instances.stream()
                .map(ServiceInstance::getHost)
                .distinct()
                .map(TaskLogAspect::resolveToIp)
                .collect(Collectors.joining(","));
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
                ? str : str.substring(0, MAX_ERROR_MSG_LEN);
    }

    private record JobSnapshot(String taskName, String serverName, String triggerType) {
        static final JobSnapshot EMPTY = new JobSnapshot(null, null, null);
    }
}
package com.rivers.ruleengine.engine;

import reactor.core.publisher.Mono;

/**
 * 用户存在性查询 SPI（审批人表达式"无变量匹配"时的兜底通道）。
 * <p>
 * 非内置 token（如 ${testUser}）在流程变量中无匹配时，回退查询该名字是否
 * 是真实用户：存在 → 作为处理人；不存在 → 保留字面原文占位。
 * <p>
 * 默认实现 {@link HttpUserLookupProvider} 经 {@code @HttpExchange} 声明式客户端
 * （UserServiceClient）调用 user-server；查询失败/超时/服务不可用返回 false
 * （降级为保留字面，行为与旧版本一致）。
 */
public interface UserLookupProvider {

    /**
     * 查询用户是否存在。
     *
     * @param userId 用户标识（token 名）
     * @return 存在 true；不存在或查询失败 false
     */
    Mono<Boolean> exists(String userId);
}

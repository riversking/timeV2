package com.rivers.ruleengine.engine;

/**
 * 网关路由业务失败（无规则命中且无默认边）。
 * <p>
 * 审批侧收到对应失败响应后将流程实例置为 FAILED，
 * 与引擎原"目标守护/求值失败 → FAILED"语义一致（消灭静默卡死）。
 */
public class RuleRouteException extends RuntimeException {

    public RuleRouteException(String message) {
        super(message);
    }
}

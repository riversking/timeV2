package com.rivers.approval.client;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.ResolveAssigneesReq;
import com.rivers.proto.ResolveAssigneesRes;
import com.rivers.proto.RouteGatewayReq;
import com.rivers.proto.RouteGatewayRes;
import com.rivers.proto.ValidateDefinitionReq;
import com.rivers.proto.ValidateDefinitionRes;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * rule-engine-server 声明式 HTTP 客户端（Spring {@code @HttpExchange}）。
 * <p>
 * url 直接使用服务名 http://rule-engine-server，由 rivers-core WebClientConfig 提供的
 * {@code @LoadBalanced} WebClient + nacos 服务发现解析实例（无需写 IP/端口）。
 * <p>
 * 请求/响应体直接使用 proto 类型；客户端编解码器由 {@code HttpClientConfig}
 * 挂载 ProtobufModule（rivers-core 客户端默认 codec 无 proto 支持）。
 * <p>
 * 仅在 {@code approval.rule-engine.mode=remote} 时经 {@code RemoteRuleEngineFacade} 使用。
 */
@HttpExchange(url = "http://rule-engine-server")
public interface RuleEngineClient {

    /**
     * 审批人表达式解析：返回保序去重处理人列表（未解析 token 保留字面原文）。
     */
    @PostExchange("/rule/resolveAssignees")
    Mono<ResultVO<ResolveAssigneesRes>> resolveAssignees(@RequestBody ResolveAssigneesReq req);

    /**
     * 排他网关路由（多出边三级降级）；业务失败返回 code!=200。
     */
    @PostExchange("/rule/routeGateway")
    Mono<ResultVO<RouteGatewayRes>> routeGateway(@RequestBody RouteGatewayReq req);

    /**
     * 定义内嵌规则校验（首个错误即返回，无错误则 errors 为空）。
     */
    @PostExchange("/rule/validateDefinition")
    Mono<ResultVO<ValidateDefinitionRes>> validateDefinition(@RequestBody ValidateDefinitionReq req);
}

package com.rivers.approval.client;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.UserDetailRes;
import com.rivers.proto.UserReq;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * user-server 声明式 HTTP 客户端（Spring {@code @HttpExchange}）。
 * <p>
 * url 直接使用服务名 http://user-server，由 rivers-core WebClientConfig 提供的
 * {@code @LoadBalanced} WebClient + nacos 服务发现解析实例（无需写 IP/端口）。
 */
@HttpExchange(url = "http://user-server")
public interface UserServiceClient {

    /**
     * 查询用户详情：存在 → code=200 且 data 非空；不存在 → code=500（HTTP 状态均为 200）。
     */
    @PostExchange("/user/getUserDetail")
    Mono<ResultVO<UserDetailRes>> getUserDetail(@RequestBody UserReq userReq);
}

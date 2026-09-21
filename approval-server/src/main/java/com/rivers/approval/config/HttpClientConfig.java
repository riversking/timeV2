package com.rivers.approval.config;

import com.rivers.approval.client.UserServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 声明式 HTTP 客户端注册：复用 rivers-core WebClientConfig（自动装配）提供的
 * HttpServiceProxyFactory 生成 {@code @HttpExchange} 接口代理。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public UserServiceClient userServiceClient(HttpServiceProxyFactory factory) {
        return factory.createClient(UserServiceClient.class);
    }
}

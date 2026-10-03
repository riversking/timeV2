package com.rivers.nba.config;

import com.rivers.nba.client.UserServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Bean
    public UserServiceClient userServiceClient(HttpServiceProxyFactory factory) {
        return factory.createClient(UserServiceClient.class);
    }
}

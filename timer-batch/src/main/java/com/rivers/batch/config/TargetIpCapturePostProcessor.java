package com.rivers.batch.config;

import com.rivers.batch.aspect.TargetIpHolder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TargetIpCapturePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof WebClient.Builder builder) {
            return builder.filter(targetIpCaptureFilter());
        }
        return bean;
    }

    private ExchangeFilterFunction targetIpCaptureFilter() {
        return (request, next) -> {
            // 此时 lb:// 已被 @LoadBalanced 的 filter 解析为真实 IP
            TargetIpHolder.set(request.url().getHost());
            return next.exchange(request)
                    .doFinally(signalType -> TargetIpHolder.clear());
        };
    }
}
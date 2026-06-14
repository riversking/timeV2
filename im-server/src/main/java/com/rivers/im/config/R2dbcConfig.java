package com.rivers.im.config;

import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import org.springframework.boot.r2dbc.autoconfigure.ProxyConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class R2dbcConfig {

    @Bean
    public ProxyConnectionFactoryCustomizer sqlLoggingCustomizer() {
        return (ProxyConnectionFactory.Builder builder) -> {
            builder.listener(new ProxyExecutionListener() {
                @Override
                public void beforeQuery(QueryExecutionInfo queryInfo) {
                    log.info(">>> R2DBC SQL: {}", queryInfo.getQueries());
                    queryInfo.getQueries().forEach(bindings ->
                            log.info(">>> Bindings: {}", bindings.getQuery())
                    );
                }

                @Override
                public void afterQuery(QueryExecutionInfo queryInfo) {
//                    log.info(">>> Execution Time: {} ms", queryInfo.getElapsedTime());
                }
            });
        };
    }
}

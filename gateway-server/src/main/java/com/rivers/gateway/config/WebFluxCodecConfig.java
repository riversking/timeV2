package com.rivers.gateway.config;

import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebFluxCodecConfig {

    @Bean
    public CodecCustomizer webFluxCodecCustomizer() {
        return configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024);
    }
}

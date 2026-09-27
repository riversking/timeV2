package com.rivers.ruleengine.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.GeneratedMessage;
import com.rivers.core.proto.ProtobufDeserializer;
import com.rivers.core.proto.ProtobufSerializer;
import com.rivers.ruleengine.client.UserServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * 声明式 HTTP 客户端注册（{@code @HttpExchange} 接口代理）。
 * <p>
 * 规则引擎出站调用：审批人表达式兜底查询 user-server（UserServiceClient）。
 * 在 rivers-core WebClientConfig（自动装配）提供的 {@code @LoadBalanced} WebClient.Builder
 * 基础上 clone 一份，并把 Jackson 编解码器替换为「挂载 ProtobufModule 的 JsonMapper」：
 * 客户端默认 codec 为纯 Jackson（无 proto 支持），直接收发 proto 类型会抛 CodecException
 * （Type definition error）。模块配置与 ProtobufJacksonConfig 对齐：序列化用 ProtobufSerializer，
 * 反序列化用 rivers-core 的通用 ProtobufDeserializer（无参原型，目标类型由 Jackson 上下文解析）。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public UserServiceClient userServiceClient(WebClient.Builder webClientBuilder) {
        JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
                .changeDefaultPropertyInclusion(incl ->
                        incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new SimpleModule("ProtobufModule")
                        .addSerializer(GeneratedMessage.class, new ProtobufSerializer<>())
                        .addDeserializer(GeneratedMessage.class, new ProtobufDeserializer<>()))
                .build();
        WebClient webClient = webClientBuilder.clone()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                })
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient)).build();
        return factory.createClient(UserServiceClient.class);
    }
}

package com.rivers.approval.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.GeneratedMessage;
import com.rivers.approval.client.RuleEngineClient;
import com.rivers.approval.client.UserServiceClient;
import com.rivers.core.proto.ProtobufDeserializer;
import com.rivers.core.proto.ProtobufSerializer;
import com.rivers.proto.ResolveAssigneesRes;
import com.rivers.proto.RouteGatewayRes;
import com.rivers.proto.UserDetailRes;
import com.rivers.proto.ValidateDefinitionRes;
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
 * 在 rivers-core WebClientConfig（自动装配）提供的 {@code @LoadBalanced} WebClient.Builder
 * 基础上 clone 一份，并把 Jackson 编解码器替换为「挂载 ProtobufModule 的 JsonMapper」：
 * 客户端默认 codec 为纯 Jackson（无 proto 支持），直接收发 proto 类型会抛 CodecException
 * （Type definition error）。序列化用 ProtobufSerializer（父类注册可命中子类）。
 * <p>
 * 反序列化注意：Jackson 3 的 SimpleDeserializers 按<b>精确类型</b>匹配，注册父类
 * GeneratedMessage 不会命中具体消息子类（会退化为普通 bean 反序列化并<b>静默丢失字段</b>），
 * 因此须按各 client 的响应消息类型逐一注册 ProtobufDeserializer（rivers-core 的通用实现，
 * 目标类型由 Jackson 上下文解析）。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public UserServiceClient userServiceClient(WebClient.Builder webClientBuilder) {
        return createClient(webClientBuilder, UserServiceClient.class, UserDetailRes.class);
    }

    /** rule-engine-server 声明式客户端（approval.rule-engine.mode=remote 时使用，proto-aware codec 同源） */
    @Bean
    public RuleEngineClient ruleEngineClient(WebClient.Builder webClientBuilder) {
        return createClient(webClientBuilder, RuleEngineClient.class,
                ResolveAssigneesRes.class, RouteGatewayRes.class, ValidateDefinitionRes.class);
    }

    /**
     * proto-aware 声明式客户端工厂：clone @LoadBalanced builder，把 Jackson 编解码器
     * 替换为「挂载 ProtobufModule 的 JsonMapper」，再经 HttpServiceProxyFactory 生成代理。
     *
     * @param protoResponseTypes 该 client 全部响应消息类型（反序列化须逐一精确注册）
     */
    @SafeVarargs
    private <T> T createClient(WebClient.Builder webClientBuilder, Class<T> clientType,
                               Class<? extends GeneratedMessage>... protoResponseTypes) {
        var protobufModule = new SimpleModule("ProtobufModule")
                .addSerializer(GeneratedMessage.class, new ProtobufSerializer<>());
        for (var protoType : protoResponseTypes) {
            registerProtoDeserializer(protobufModule, protoType);
        }
        JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
                .changeDefaultPropertyInclusion(incl ->
                        incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(protobufModule)
                .build();
        WebClient webClient = webClientBuilder.clone()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                })
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient)).build();
        return factory.createClient(clientType);
    }

    /**
     * 精确注册单个 proto 响应类型的反序列化器。
     * 独立成泛型方法以统一类型变量，规避循环通配符捕获（capture）导致的泛型不兼容。
     */
    private static <P extends GeneratedMessage> void registerProtoDeserializer(
            SimpleModule module, Class<P> protoType) {
        module.addDeserializer(protoType, new ProtobufDeserializer<>(protoType));
    }
}

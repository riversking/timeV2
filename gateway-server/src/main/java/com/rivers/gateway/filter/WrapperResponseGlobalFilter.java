package com.rivers.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class WrapperResponseGlobalFilter implements GlobalFilter, Ordered {

    private static final String EXPORT_PATH = "export";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ... 前面的代码保持不变 ...
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        if (exchange.getRequest().getPath().value().contains(EXPORT_PATH)) {
            return chain.filter(exchange);
        }

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @NonNull
            public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                // 1. 安全地转换 Publisher
                Flux<DataBuffer> flux = Flux.from(body).cast(DataBuffer.class);

                // 2. 使用 DataBufferUtils.join 聚合响应体
                Mono<DataBuffer> allDataBufferMono = DataBufferUtils.join(flux);

                return allDataBufferMono.flatMap(originalBuffer -> {
                    // 将 DataBuffer 内容读取到字节数组中，以便立即释放原始 DataBuffer
                    byte[] originalBytes = new byte[originalBuffer.readableByteCount()];
                    originalBuffer.read(originalBytes);
                    // 立即释放聚合后的原始 DataBuffer
                    DataBufferUtils.release(originalBuffer);

                    String originalBody = new String(originalBytes, StandardCharsets.UTF_8);
                    log.info("Original Response Body: {}", originalBody);

                    // 根据状态码修改内容
                    byte[] modifiedBytes = switch (getStatusCode()) {
                        case null -> {
                            log.warn("Response status was null, returning raw content.");
                            yield originalBytes;
                        }
                        case HttpStatus.OK, HttpStatus.UNAUTHORIZED -> originalBytes;
                        default -> {
                            try {
                                JSONObject jsonObject = JSON.parseObject(originalBody);
                                if (jsonObject != null) {
                                    jsonObject.put("code", jsonObject.get("status"));
                                    jsonObject.remove("status");
                                    yield jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8);
                                } else {
                                    log.warn("Failed to parse JSON, response is null.");
                                    yield "{\"code\": 500, \"message\": \"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse or modify JSON response", e);
                                yield "{\"code\": 500, \"message\": \"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
                            }
                        }
                    };

                    // 3. 使用 usingWhen 来管理新创建的 DataBuffer
                    // 资源提供者: 创建新的 DataBuffer
                    Mono<DataBuffer> resourceSupplier = Mono.fromCallable(() -> bufferFactory.wrap(modifiedBytes));

                    // 资源使用者: 将 DataBuffer 写入响应
                    // 资源清理器: 无论成功、失败还是取消，都释放 DataBuffer
                    return Mono.usingWhen(
                            resourceSupplier,
                            modifiedBuffer -> getDelegate().writeWith(Mono.just(modifiedBuffer)),
                            modifiedBuffer -> {
                                log.debug("Releasing modified buffer.");
                                DataBufferUtils.release(modifiedBuffer);
                                return Mono.empty();
                            }
                    );
                });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}

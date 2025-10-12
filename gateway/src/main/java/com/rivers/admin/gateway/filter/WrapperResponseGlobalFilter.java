package com.rivers.admin.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.NonNull;
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
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class WrapperResponseGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        // 使用模式匹配来简化条件判断
        if (exchange.getRequest().getPath().value().contains("export")) {
            return chain.filter(exchange);
        }

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @NonNull
            public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<?> flux) {
                    return super.writeWith(
                            flux.cast(DataBuffer.class)
                                    .collectList()
                                    .flatMap(dataBuffers -> {
                                        try (var allContent = aggregateDataBuffers(dataBuffers)) {
                                            String responseData = allContent.toString(StandardCharsets.UTF_8);
                                            log.info("响应内容: {}", responseData);
                                            log.info("getStatusCode() {}", getStatusCode());
                                            // 这里继续使用 switch 表达式是合理的，因为它有多个 case
                                            byte[] uppedContent = switch (getStatusCode()) {
                                                case null -> { // 处理 null 的情况
                                                    log.warn("Response status was not set, defaulting to INTERNAL_SERVER_ERROR");
                                                    yield allContent.toByteArray(); // 或者返回一个错误信息的 JSON
                                                }
                                                case HttpStatus.OK, HttpStatus.UNAUTHORIZED -> allContent.toByteArray();
                                                default -> {
                                                    JSONObject jsonObject = JSON.parseObject(responseData);
                                                    jsonObject.put("code", jsonObject.get("status"));
                                                    jsonObject.remove("status");
                                                    yield jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8);
                                                }
                                            };
                                            return Mono.just(bufferFactory.wrap(uppedContent));
                                        } catch (Exception e) {
                                            return Mono.error(e);
                                        }
                                    })
                    );
                } else {
                    return super.writeWith(body);
                }
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 一个辅助方法，用于聚合 DataBuffer 列表，并确保它们被释放。
     * 返回一个 ByteArrayOutputStream，它会在 try-with-resources 块结束时自动关闭。
     */
    private ByteArrayOutputStream aggregateDataBuffers(List<DataBuffer> dataBuffers) {
        int totalLength = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(totalLength);
        for (DataBuffer buffer : dataBuffers) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            outputStream.writeBytes(bytes);
            DataBufferUtils.release(buffer); // 确保释放
        }
        return outputStream;
    }


    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}

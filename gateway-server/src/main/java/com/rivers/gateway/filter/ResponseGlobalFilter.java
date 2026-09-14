package com.rivers.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ResponseGlobalFilter implements GlobalFilter, Ordered {

    private static final String EXPORT_PATH = "export";
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final int MAX_ERROR_BODY_BUFFER_SIZE = 64 * 1024;
    private static final int LOG_MAX_BYTES = 500;

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @NullMarked
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode status = getStatusCode();
                boolean needsFallback = status != null && status.isError()
                        && !exchange.getRequest().getPath().value().contains(EXPORT_PATH)
                        && !getHeaders().containsHeader(HttpHeaders.CONTENT_ENCODING);
                if (!needsFallback) {
                    return super.writeWith(body);
                }
                return DataBufferUtils.join(body, MAX_ERROR_BODY_BUFFER_SIZE)
                        .map(db -> {
                            byte[] bytes = new byte[db.readableByteCount()];
                            db.read(bytes);
                            DataBufferUtils.release(db);  // 幂等的：已校验 isAllocated()
                            return bytes;
                        })
                        .defaultIfEmpty(EMPTY_BODY)
                        .flatMap(bytes -> {
                            logErrorBody(status, bytes);
                            byte[] finalBytes = bytes.length > 0 ? bytes : fallbackErrorBody(status);
                            getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
                            getHeaders().setContentLength(finalBytes.length);
                            return getDelegate().writeWith(
                                    Flux.just(bufferFactory.wrap(finalBytes)));
                        })
                        .onErrorResume(DataBufferLimitException.class, e -> {
                            log.warn("错误响应体超过 {} 字节，使用统一兜底报文: {}",
                                    MAX_ERROR_BODY_BUFFER_SIZE, exchange.getRequest().getPath().value());
                            byte[] finalBytes = fallbackErrorBody(status);
                            getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
                            getHeaders().setContentLength(finalBytes.length);
                            return getDelegate().writeWith(Flux.just(bufferFactory.wrap(finalBytes)));
                        });
            }

            @Override
            @NullMarked
            public Mono<Void> writeAndFlushWith(
                    Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(Flux::from));
            }

            private void logErrorBody(HttpStatusCode status, byte[] contentBytes) {
                if (!log.isInfoEnabled()) {
                    return;
                }
                int logLen = Math.min(contentBytes.length, LOG_MAX_BYTES);
                String snippet = new String(contentBytes, 0, logLen, StandardCharsets.UTF_8);
                if (contentBytes.length > LOG_MAX_BYTES) {
                    log.info("Error Response Body (status={}, truncated {}/{} bytes): {}...",
                            status, LOG_MAX_BYTES, contentBytes.length, snippet);
                } else {
                    log.info("Error Response Body (status={}): {}", status, snippet);
                }
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /** 空错误体的统一兜底报文，code 与真实状态码保持一致
     */
    private static byte[] fallbackErrorBody(HttpStatusCode status) {
        return ("{\"code\":" + status.value() + ",\"message\":\"upstream error\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
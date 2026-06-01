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
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
@Slf4j
public class ResponseGlobalFilter implements GlobalFilter, Ordered {

    private static final String EXPORT_PATH = "export";
    private static final String DEFAULT_RES = "{\"code\": 500, \"message\": \"Internal Server Error\"}";
    private static final byte[] DEFAULT_RES_BYTES = DEFAULT_RES.getBytes(StandardCharsets.UTF_8);

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        HttpStatusCode statusCode = originalResponse.getStatusCode();
        if (Objects.equals(HttpStatus.OK, statusCode)) {
            return chain.filter(exchange);
        }
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @NullMarked
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (exchange.getRequest().getPath().value().contains(EXPORT_PATH)) {
                    return super.writeWith(body);
                }
                return DataBufferUtils.join(body)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Response body is empty for status: {}", getStatusCode());
                            return Mono.just(bufferFactory.wrap(DEFAULT_RES_BYTES));
                        }))
                        .flatMap(originalBuffer -> Mono.using(
                                () -> originalBuffer,
                                buffer -> {
                                    byte[] contentBytes = new byte[buffer.readableByteCount()];
                                    buffer.read(contentBytes);
                                    String originalBody = new String(contentBytes, StandardCharsets.UTF_8);
                                    log.info("Original Response Body: {}", originalBody);
                                    byte[] finalBytes = contentBytes;
                                    HttpStatusCode currentStatus = getStatusCode();
                                    if (currentStatus == null) {
                                        log.warn("Response status was null, returning raw content.");
                                    } else if (currentStatus != HttpStatus.UNAUTHORIZED) {
                                        finalBytes = DEFAULT_RES_BYTES;
                                    }
                                    getHeaders().setContentLength(finalBytes.length);
                                    setStatusCode(HttpStatus.OK);
                                    return getDelegate().writeWith(Flux.just(bufferFactory.wrap(finalBytes)));
                                },
                                DataBufferUtils::release
                        ));
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}

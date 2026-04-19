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
    public static final String DEFAULT_RES = "{\"code\": 500, \"message\": \"Internal Server Error\"}";

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @NullMarked
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (exchange.getRequest().getPath().value().contains(EXPORT_PATH)) {
                    return super.writeWith(body);
                }
                Flux<DataBuffer> flux = Flux.from(body).cast(DataBuffer.class);
                Mono<DataBuffer> allDataBufferMono = DataBufferUtils.join(flux);
                return allDataBufferMono.flatMap(originalBuffer -> {
                    byte[] originalBytes = new byte[originalBuffer.readableByteCount()];
                    originalBuffer.read(originalBytes);
                    String originalBody = new String(originalBytes, StandardCharsets.UTF_8);
                    log.info("Original Response Body: {}", originalBody);
                    byte[] modifiedBytes = switch (getStatusCode()) {
                        case null -> {
                            log.warn("Response status was null, returning raw content.");
                            yield originalBytes;
                        }
                        case HttpStatus.OK, HttpStatus.UNAUTHORIZED -> originalBytes;
                        default -> DEFAULT_RES.getBytes(StandardCharsets.UTF_8);
                    };
                    getHeaders().setContentLength(modifiedBytes.length);
                    DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);
                    setStatusCode(HttpStatus.OK);
                    return Mono.usingWhen(
                            Mono.just(modifiedBuffer),
                            mb -> {
                                DataBufferUtils.release(originalBuffer);
                                return getDelegate().writeWith(Flux.just(mb));
                            },
                            mb -> Mono.fromCallable(() -> DataBufferUtils.release(mb)),
                            (mb, _) -> Mono.fromRunnable(() -> DataBufferUtils.release(mb)),
                            mb -> Mono.fromRunnable(() -> DataBufferUtils.release(mb))
                    );
                }).onErrorResume(e -> {
                    log.error("Error occurred while processing response", e);
                    return handleFallbackResponse();
                });
            }

            private Mono<Void> handleFallbackResponse() {
                DataBuffer buffer = bufferFactory.wrap(DEFAULT_RES.getBytes(StandardCharsets.UTF_8));
                return getDelegate().writeWith(Flux.just(buffer))
                        .doFinally(signalType -> DataBufferUtils.release(buffer));
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER;
    }
}

package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.service.IWsTicketService;
import com.rivers.proto.CreateTicketReq;
import com.rivers.proto.CreateTicketRes;
import com.rivers.proto.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsTicketServiceImpl implements IWsTicketService {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    private static final String TICKET_PREFIX = "ws:ticket:";

    @Override
    public Mono<ResultVO<CreateTicketRes>> createTicket(CreateTicketReq createTicketReq) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        LoginUser loginUser = createTicketReq.getLoginUser();
        String userId = loginUser.getUserId();
        return reactiveStringRedisTemplate.opsForValue()
                .set(TICKET_PREFIX + ticket, userId, Duration.ofSeconds(30))
                .flatMap(success -> {
                    // 3. 🌟 校验 Redis 是否真正写入成功
                    if (success) {
                        CreateTicketRes res = CreateTicketRes.newBuilder()
                                .setTicket(ticket)
                                .build();
                        return Mono.just(ResultVO.ok(res));
                    } else {
                        log.error("Redis 写入 Ticket 失败, userId: {}", userId);
                        return Mono.just(ResultVO.<CreateTicketRes>fail("系统繁忙，请稍后再试"));
                    }
                }).onErrorResume(e -> {
                    log.error("创建ticket失败", e);
                    return Mono.just(ResultVO.fail("创建ticket失败"));
                });
    }

    @Override
    public Mono<String> consumeTicket(String ticket) {
        return reactiveStringRedisTemplate.opsForValue().getAndDelete(TICKET_PREFIX + ticket);
    }
}

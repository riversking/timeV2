package com.rivers.approval.engine;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 默认上级链实现：从流程变量 leaderChain 读取。
 *
 * <p>调用方（按公司结构算好）在 startProcess.variables 传入，
 * 例：{"leaderChain": ["mgr001", "dir001"]}，第 k 个 $leader 取第 k 个元素。
 */
@Component
public class VariablesLeaderProvider implements LeaderProvider {

    private static final String LEADER_CHAIN = "leaderChain";

    @Override
    public Mono<List<String>> resolveChain(String startUser, int level, Map<String, Object> variables) {
        if (level <= 0 || variables == null || variables.isEmpty()) {
            return Mono.just(List.of());
        }
        if (!(variables.get(LEADER_CHAIN) instanceof Collection<?> chain)) {
            return Mono.just(List.of());
        }
        return Mono.just(chain.stream()
                .map(v -> v == null ? "" : String.valueOf(v).trim())
                .filter(s -> !s.isBlank())
                .limit(level)
                .toList());
    }
}

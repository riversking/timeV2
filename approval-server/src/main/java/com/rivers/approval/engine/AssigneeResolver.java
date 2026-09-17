package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 审批人表达式解析器（USER_TASK 节点到达时求值）。
 *
 * <p>表达式（节点 config.candidateExpr）中 token 按序解析，两种写法等价：
 * <ul>
 *   <li>$leader / ${leader}          — 上级链逐级：第 k 个 token 取第 k 级（经理批完 总监再批，由 SEQUENTIAL 承接）</li>
 *   <li>$startUser / ${startUser}    — 发起人（instance.initiator）</li>
 *   <li>$其他名 / ${其他名}           — 流程变量同名取值（String=1人，数组/逗号串=多人，即"调用者传参"通道）</li>
 *   <li>未解析 token                  — 保留字面原文（$name / ${name}），同名变量一旦传入即自动生效</li>
 * </ul>
 *
 * <p>示例：$startUser（发起人审批）、$leader（一级领导）、$leader$leader（两级串签）。
 * <p>输出有序处理人列表（保序去重）；空列表表示表达式缺失/无 token，由调用方回退静态配置。
 */
@Component
public class AssigneeResolver {

    /** $name 与 ${name} 双语法：#1=${} 内名称，#2=裸名称 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)}|\\$([A-Za-z0-9_]+)");
    private static final String START_USER = "startUser";
    private static final String LEADER = "leader";

    private final LeaderProvider leaderProvider;

    public AssigneeResolver(LeaderProvider leaderProvider) {
        this.leaderProvider = leaderProvider;
    }

    /**
     * 解析表达式为处理人列表（保序去重；未解析 token 保留字面原文）。
     */
    public Mono<List<String>> resolve(String expr, FlowInstance instance, Map<String, Object> variables) {
        var tokens = parseTokens(expr);
        if (tokens.isEmpty()) {
            return Mono.just(List.of());
        }
        var vars = variables != null ? variables : Map.<String, Object>of();
        var initiator = instance != null ? instance.getInitiator() : null;
        var leaderLevels = (int) tokens.stream().filter(t -> LEADER.equals(t.name())).count();
        var chainMono = leaderLevels > 0
                ? leaderProvider.resolveChain(initiator, leaderLevels, vars)
                : Mono.just(List.<String>of());
        return chainMono.map(chain -> resolveTokens(tokens, chain, initiator, vars));
    }

    private List<String> resolveTokens(List<Token> tokens,
                                       List<String> leaderChain,
                                       String initiator,
                                       Map<String, Object> variables) {
        var result = new ArrayList<String>();
        var leaderLevel = 0;
        for (var token : tokens) {
            List<String> resolved;
            if (START_USER.equals(token.name())) {
                resolved = initiator == null || initiator.isBlank()
                        ? List.of() : List.of(initiator);
            } else if (LEADER.equals(token.name())) {
                var leader = leaderLevel < leaderChain.size() ? leaderChain.get(leaderLevel) : null;
                leaderLevel++;
                resolved = leader == null || leader.isBlank() ? List.of() : List.of(leader);
            } else {
                resolved = fromVariable(variables, token.name());
            }
            if (resolved.isEmpty()) {
                addUnique(result, token.raw());
            } else {
                resolved.forEach(user -> addUnique(result, user));
            }
        }
        return result;
    }

    /**
     * 变量取值：数组 → 逐个取；字符串 → 逗号拆分（"u1,u2" 兼容多人）。
     */
    private List<String> fromVariable(Map<String, Object> variables, String name) {
        var value = variables.get(name);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(v -> v == null ? "" : String.valueOf(v).trim())
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * tokenize：支持 $name 与 ${name} 相邻书写（如 $leader$leader），裸名称仅限字母/数字/下划线。
     */
    private List<Token> parseTokens(String expr) {
        if (expr == null || expr.isBlank()) {
            return List.of();
        }
        var tokens = new ArrayList<Token>();
        var matcher = TOKEN_PATTERN.matcher(expr);
        while (matcher.find()) {
            var braced = matcher.group(1);
            var name = (braced != null ? braced : matcher.group(2)).trim();
            if (!name.isEmpty()) {
                tokens.add(new Token(name, matcher.group()));
            }
        }
        return tokens;
    }

    private static void addUnique(List<String> target, String user) {
        if (!target.contains(user)) {
            target.add(user);
        }
    }

    /**
     * 解析出的 token：名称 + 原文（未解析时按原文保留占位）
     */
    private record Token(String name, String raw) {
    }
}

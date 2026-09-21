package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 审批人表达式解析器（USER_TASK 节点到达时求值）。
 *
 * <p>表达式（节点 config.candidateExpr）中 token 按序解析，两种写法等价：
 * <ul>
 *   <li>$leader / ${leader}          — 上级链逐级：第 k 个 token 取第 k 级（经理批完 总监再批，由 SEQUENTIAL 承接）</li>
 *   <li>$leaderMax / ${leaderMax}    — 全链逐级：从直接上级一直审批到最大领导（leaderChain 全量，多人由 SEQUENTIAL 承接）</li>
 *   <li>$startUser / ${startUser}    — 发起人（instance.initiator）</li>
 *   <li>$其他名 / ${其他名}           — 流程变量同名取值（String=1人，数组/逗号串=多人，即"调用者传参"通道）</li>
 *   <li>变量无匹配的 token             — 兜底查询用户信息（{@link UserLookupProvider}）：该名字为真实用户则解析为其本人，否则保留字面原文</li>
 *   <li>无 $token（如 userF / userF,userG）— 静态直写处理人（直接配置审批人，无需 ${} 格式）</li>
 * </ul>
 *
 * <p>示例：$startUser（发起人审批）、$leader（一级领导）、$leader$leader（两级串签）、
 * $leaderMax（全链逐级到最大领导）。
 * <p>输出有序处理人列表（保序去重）；空列表表示表达式缺失/无 token，由调用方回退静态配置。
 */
@Component
public class AssigneeResolver {

    /** $name 与 ${name} 双语法：#1=${} 内名称，#2=裸名称 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)}|\\$([A-Za-z0-9_]+)");
    private static final String START_USER = "startUser";
    private static final String LEADER = "leader";
    /** $leaderMax — 全链逐级：从直接上级一直审批到最大领导（leaderChain 全量） */
    private static final String LEADER_MAX = "leaderMax";

    private final LeaderProvider leaderProvider;
    private final UserLookupProvider userLookupProvider;

    public AssigneeResolver(LeaderProvider leaderProvider, UserLookupProvider userLookupProvider) {
        this.leaderProvider = leaderProvider;
        this.userLookupProvider = userLookupProvider;
    }

    /**
     * 解析表达式为处理人列表（保序去重）。
     * 变量无匹配的非内置 token 先查询用户信息兜底：存在 → 用该用户；否则保留字面原文。
     */
    public Mono<List<String>> resolve(String expr, FlowInstance instance, Map<String, Object> variables) {
        var tokens = parseTokens(expr);
        if (tokens.isEmpty()) {
            // 无 $token：静态直写处理人（如 userF / userF,userG）；空表达式返回空，由调用方回退静态配置
            return Mono.just(staticHandlers(expr));
        }
        var vars = variables != null ? variables : Map.<String, Object>of();
        var initiator = instance != null ? instance.getInitiator() : null;
        var hasLeaderMax = tokens.stream().anyMatch(t -> LEADER_MAX.equals(t.name()));
        var leaderLevels = hasLeaderMax
                ? Integer.MAX_VALUE
                : (int) tokens.stream().filter(t -> LEADER.equals(t.name())).count();
        var chainMono = leaderLevels > 0
                ? leaderProvider.resolveChain(initiator, leaderLevels, vars)
                : Mono.just(List.<String>of());
        return chainMono.flatMap(chain -> resolveTokens(tokens, chain, initiator, vars));
    }

    private Mono<List<String>> resolveTokens(List<Token> tokens,
                                             List<String> leaderChain,
                                             String initiator,
                                             Map<String, Object> variables) {
        var slots = new ArrayList<Slot>();
        var pending = new LinkedHashSet<String>();
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
            } else if (LEADER_MAX.equals(token.name())) {
                // 全链：从当前级别起展开剩余全部领导（单独使用时即整条 leaderChain）
                resolved = leaderLevel < leaderChain.size()
                        ? new ArrayList<>(leaderChain.subList(leaderLevel, leaderChain.size()))
                        : List.of();
                leaderLevel = leaderChain.size();
            } else {
                resolved = fromVariable(variables, token.name());
                if (resolved.isEmpty()) {
                    // 变量无匹配 → 兜底通道：查询用户信息该名字是否为真实用户（存在用名字，否则保留字面）
                    slots.add(new Slot(token, null));
                    pending.add(token.name());
                    continue;
                }
            }
            slots.add(new Slot(token, resolved));
        }
        if (pending.isEmpty()) {
            return Mono.just(assemble(slots, Map.of()));
        }
        return Flux.fromIterable(pending)
                .concatMap(name -> userLookupProvider.exists(name)
                        .defaultIfEmpty(false)
                        .map(exists -> Map.entry(name, exists)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(found -> assemble(slots, found));
    }

    /**
     * 组装最终处理人列表（保序去重）。
     * 兜底槽位（resolved=null）：用户信息确认存在 → 用 token 名；否则保留字面原文。
     */
    private List<String> assemble(List<Slot> slots, Map<String, Boolean> userExists) {
        var result = new ArrayList<String>();
        for (var slot : slots) {
            if (slot.resolved() == null) {
                if (Boolean.TRUE.equals(userExists.get(slot.token().name()))) {
                    addUnique(result, slot.token().name());
                } else {
                    addUnique(result, slot.token().raw());
                }
            } else if (slot.resolved().isEmpty()) {
                addUnique(result, slot.token().raw());
            } else {
                slot.resolved().forEach(user -> addUnique(result, user));
            }
        }
        return result;
    }

    /**
     * 静态直写处理人：表达式无 $token 时（如 "userF" / "userF,userG"），逗号拆分（保序去重）。
     */
    private List<String> staticHandlers(String expr) {
        if (expr == null || expr.isBlank()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var part : expr.split(",")) {
            var user = part.trim();
            if (!user.isBlank()) {
                addUnique(result, user);
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
     * 解析槽位：resolved=null 表示变量无匹配、待用户信息兜底确认
     */
    private record Slot(Token token, List<String> resolved) {
    }

    /**
     * 解析出的 token：名称 + 原文（未解析时按原文保留占位）
     */
    private record Token(String name, String raw) {
    }
}

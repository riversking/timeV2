package com.rivers.approval.engine;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 上级链提供者（SPI，组织架构接入点）。
 *
 * <p>默认实现 {@link VariablesLeaderProvider} 从流程变量读取；
 * user-server 具备部门/上级数据后，新增实现即可让 $leader 按公司结构逐级解析。
 */
public interface LeaderProvider {

    /**
     * 解析发起人向上 level 级的上级链（L1=直接上级，L2=L1 的上级…）。
     *
     * @param startUser 链条起点（发起人）
     * @param level     $leader token 个数
     * @param variables 流程变量
     * @return 已解析的上级 userId 列表（长度 ≤ level；不足即视为未解析，由解析器保留占位）
     */
    Mono<List<String>> resolveChain(String startUser, int level, Map<String, Object> variables);
}

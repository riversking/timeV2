package com.rivers.approval.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record ProcessDefinition(
        String key,
        String name,
        List<NodeDef> nodes,
        List<EdgeDef> edges
) {
    public ProcessDefinition {
        nodes = nodes != null ? Collections.unmodifiableList(nodes) : List.of();
        edges = edges != null ? Collections.unmodifiableList(edges) : List.of();
    }

    /**
     * 按 id 查节点
     */
    public Optional<NodeDef> nodeById(String nodeId) {
        return nodes.stream().filter(n -> n.id().equals(nodeId)).findFirst();
    }

    /**
     * 按 source 查所有出边
     */
    public List<EdgeDef> edgesFrom(String sourceNodeId) {
        return edges.stream().filter(e -> e.source().equals(sourceNodeId)).toList();
    }

    /**
     * 查后继节点列表（沿 edges 推导）
     */
    public List<NodeDef> successorsOf(String sourceNodeId) {
        return edgesFrom(sourceNodeId).stream()
                .map(e -> nodeById(e.target()))
                .flatMap(Optional::stream)
                .toList();
    }
}

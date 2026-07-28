package com.rivers.approval.model;

public record EdgeDef(
        String id,
        String source,
        String target,
        /* SpEL 条件表达式，排他网关使用，非网关边可为 null */
        String conditionExpression
) {
}

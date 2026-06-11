package com.leucine.setup.audit;

public record AuditEntry(
    long id,
    String connectionId,
    String operation,
    String payloadJson,
    String sqlText,
    AuditStatus status,
    String error,
    String operator,
    long startedAt,
    Long finishedAt
) {
}

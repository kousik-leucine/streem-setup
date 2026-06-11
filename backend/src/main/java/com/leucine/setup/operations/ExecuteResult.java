package com.leucine.setup.operations;

import java.util.Map;

public record ExecuteResult(
    long auditId,
    Map<String, Object> generatedIds,
    String summary
) {
}

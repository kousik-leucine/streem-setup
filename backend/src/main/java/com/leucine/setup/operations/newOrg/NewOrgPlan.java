package com.leucine.setup.operations.newOrg;

import java.util.List;
import java.util.Map;

/** The statements an operation intends to run, plus the IDs it generated. */
public record NewOrgPlan(List<SqlStatement> statements, Map<String, Object> generatedIds) {
}

package com.leucine.setup.operations;

import java.util.List;

/**
 * What an operation reports back from preview(). The SQL is shown to the operator
 * verbatim; warnings flag preflight concerns that don't block execution.
 */
public record PreviewResult(
    String sql,
    String summary,
    List<String> warnings
) {
  public static PreviewResult of(String sql, String summary) {
    return new PreviewResult(sql, summary, List.of());
  }
}

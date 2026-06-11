package com.leucine.setup.operations.newOrg;

import java.util.List;

/**
 * One INSERT (or UPDATE) we plan to run. We keep the parameterized template
 * for execute() and a list of values; the preview renderer inlines values into
 * a copy of the template for display only.
 */
public record SqlStatement(String comment, String template, List<Object> params) {

  public String render() {
    String[] parts = template.split("\\?", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      out.append(parts[i]);
      if (i < params.size()) {
        out.append(literal(params.get(i)));
      }
    }
    return out.toString();
  }

  private static String literal(Object v) {
    if (v == null) return "NULL";
    if (v instanceof Number || v instanceof Boolean) return v.toString();
    if (v instanceof byte[]) return "<binary>";
    String s = v.toString().replace("'", "''");
    return "'" + s + "'";
  }
}

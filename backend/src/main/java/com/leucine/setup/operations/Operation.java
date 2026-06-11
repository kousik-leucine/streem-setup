package com.leucine.setup.operations;

/**
 * One unit of work against a target Postgres. Every operation is a preview/execute
 * pair: preview() reports what would happen, execute() runs it in a transaction.
 *
 * The runtime never calls execute() directly with stale preview output — both
 * phases take the same payload, and execute() re-runs preflight checks itself.
 */
public interface Operation<P> {

  /** Stable identifier, e.g. "NEW_ORG" — used as the audit_log.operation value. */
  String name();

  /** Build the SQL + warnings without writing anything to the target. */
  PreviewResult preview(P payload, OperationContext ctx);

  /** Run inside a single transaction. Returns generated IDs for caller display. */
  ExecuteResult execute(P payload, OperationContext ctx);

  /** PROD targets force-confirm before destructive ops. Read-only ops return false. */
  default boolean isDestructive() { return true; }
}

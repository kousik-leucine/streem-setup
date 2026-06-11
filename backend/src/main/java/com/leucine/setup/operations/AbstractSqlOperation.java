package com.leucine.setup.operations;

import com.leucine.setup.operations.newOrg.SqlStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Common preview + execute shape for operations that produce a sequence of
 * INSERT/UPDATE statements. Subclasses provide name + preflight + the statement plan.
 */
public abstract class AbstractSqlOperation<P> implements Operation<P> {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** Read-only checks. Throw IllegalArgumentException to block; return list of warnings otherwise. */
  protected abstract List<String> preflight(P payload, JdbcTemplate target);

  /** Build the sequence of statements + the map of generated IDs to report back. */
  protected abstract Plan plan(P payload, IdGenerator ids);

  /** One-line description shown above the SQL preview. */
  protected abstract String summary(P payload, Plan plan);

  @Override
  public PreviewResult preview(P payload, OperationContext ctx) {
    List<String> warnings = preflight(payload, ctx.target());
    Plan plan = plan(payload, ctx.ids());
    return new PreviewResult(renderAll(plan.statements()), summary(payload, plan), warnings);
  }

  @Override
  public ExecuteResult execute(P payload, OperationContext ctx) {
    preflight(payload, ctx.target());
    Plan plan = plan(payload, ctx.ids());
    String sql = renderAll(plan.statements());
    log.info("Executing {} against {} ({} statements)",
        name(), ctx.connection().name(), plan.statements().size());

    JdbcTemplate target = ctx.target();
    TransactionTemplate tx = new TransactionTemplate(
        new DataSourceTransactionManager(target.getDataSource()));
    tx.execute(new TransactionCallbackWithoutResult() {
      @Override
      protected void doInTransactionWithoutResult(TransactionStatus status) {
        for (SqlStatement s : plan.statements()) {
          target.update(s.template(), s.params().toArray());
        }
      }
    });
    return new ExecuteResult(0L, plan.generatedIds(), summary(payload, plan));
  }

  protected static String renderAll(List<SqlStatement> stmts) {
    return stmts.stream()
        .map(s -> "-- " + s.comment() + "\n" + s.render() + ";")
        .collect(Collectors.joining("\n\n"));
  }

  /** Mutable builder used inside plan(); produces an immutable Plan. */
  public static final class PlanBuilder {
    private final List<SqlStatement> stmts = new ArrayList<>();
    private final Map<String, Object> ids = new LinkedHashMap<>();

    public PlanBuilder add(String comment, String template, List<Object> params) {
      stmts.add(new SqlStatement(comment, template, params));
      return this;
    }

    public PlanBuilder id(String key, Object value) {
      ids.put(key, value);
      return this;
    }

    public Plan build() {
      return new Plan(List.copyOf(stmts), Map.copyOf(ids));
    }
  }

  public record Plan(List<SqlStatement> statements, Map<String, Object> generatedIds) {}
}

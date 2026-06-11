package com.leucine.setup.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditRepository repo;

  public AuditService(AuditRepository repo) {
    this.repo = repo;
  }

  /** Begin a new audit row in PREVIEW state. Returned id is updated on completion. */
  public long start(String connectionId, String operation, String payloadJson, String sqlText) {
    AuditEntry e = new AuditEntry(
        0, connectionId, operation, payloadJson, sqlText,
        AuditStatus.PREVIEW, null, currentOperator(), System.currentTimeMillis(), null);
    return repo.insert(e);
  }

  public void succeed(long id, String sqlText) {
    repo.update(id, AuditStatus.SUCCESS, sqlText, null, System.currentTimeMillis());
  }

  public void fail(long id, String sqlText, Throwable t) {
    String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
    repo.update(id, AuditStatus.FAILED, sqlText, msg, System.currentTimeMillis());
  }

  private static String currentOperator() {
    String env = System.getenv("USERNAME");
    if (env == null) env = System.getenv("USER");
    if (env == null) env = System.getProperty("user.name");
    return env;
  }
}

package com.leucine.setup.connection.dto;

public record TestConnectionResult(
    boolean ok,
    String error,
    String serverVersion
) {
  public static TestConnectionResult success(String serverVersion) {
    return new TestConnectionResult(true, null, serverVersion);
  }

  public static TestConnectionResult failure(String error) {
    return new TestConnectionResult(false, error, null);
  }
}

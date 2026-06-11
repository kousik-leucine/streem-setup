package com.leucine.setup.operations;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Epoch-millis based ID generator, matches the convention used in the original
 * streem-backend setup SQL. Within a single operation we increment monotonically
 * from the start epoch to guarantee no within-op collisions, even if multiple
 * IDs are generated in the same millisecond.
 */
public class IdGenerator {

  private final AtomicLong counter;

  public IdGenerator() {
    this.counter = new AtomicLong(System.currentTimeMillis());
  }

  public IdGenerator(long startAt) {
    this.counter = new AtomicLong(startAt);
  }

  public long next() {
    return counter.getAndIncrement();
  }
}

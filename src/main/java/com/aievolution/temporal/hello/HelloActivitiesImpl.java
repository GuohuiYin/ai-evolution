package com.aievolution.temporal.hello;

import java.util.concurrent.atomic.AtomicInteger;

/** {@link HelloActivities} 实现：step=2 首次调用抛瞬态故障，验证 activity 重试语义。 */
public class HelloActivitiesImpl implements HelloActivities {

  private final AtomicInteger step2Attempts = new AtomicInteger();

  @Override
  public String executeStep(String input, int step) {
    if (step == 2 && step2Attempts.incrementAndGet() == 1) {
      throw new RuntimeException("simulated transient failure on step 2 (attempt 1)");
    }
    return "step " + step + " processed [" + input + "] attempts(step2)=" + step2Attempts.get();
  }
}

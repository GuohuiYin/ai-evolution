package com.aievolution.temporal.hello;

import io.temporal.activity.ActivityInterface;

/** 最小环 activity：所有副作用（IO/外部调用/非确定性）必须进 activity，workflow 本体保持确定性可重放。 */
@ActivityInterface
public interface HelloActivities {

  /** 执行第 step 步；实现里第 2 步首次调用故意抛错，演示服务端按 RetryOptions 自动重试。 */
  String executeStep(String input, int step);
}

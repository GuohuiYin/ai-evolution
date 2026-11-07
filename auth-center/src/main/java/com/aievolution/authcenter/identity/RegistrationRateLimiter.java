package com.aievolution.authcenter.identity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 注册限流（W30 #6）：每 IP 每小时 N 次（配置化，A11 策略不硬编码），防批量注册。
 *
 * <p>内存固定窗口（按小时对齐自然时刻），单实例实现——与主工程 RateLimitFilter 同姿势； 只卡 POST /register，登录与审批面不受影响。超限由控制器返回 429。
 */
@Component
class RegistrationRateLimiter {

  private static final long WINDOW_SECONDS = 3600;

  private final int maxPerHour;
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

  private static final class Window {
    private final long windowStart;
    private final AtomicInteger count;

    private Window(long windowStart) {
      this.windowStart = windowStart;
      this.count = new AtomicInteger(1);
    }
  }

  RegistrationRateLimiter(@Value("${auth.registration.rate-limit-per-hour:5}") int maxPerHour) {
    if (maxPerHour < 1) {
      throw new IllegalStateException(
          "auth.registration.rate-limit-per-hour 必须 ≥ 1: " + maxPerHour);
    }
    this.maxPerHour = maxPerHour;
  }

  /** 该 IP 本次注册是否放行（计数在询问时即消耗——失败的注册尝试同样占额度，撞库不是免费通道） */
  boolean tryAcquire(String ip) {
    long now = System.currentTimeMillis() / 1000;
    long windowStart = now - (now % WINDOW_SECONDS);
    Window window =
        windows.compute(
            ip,
            (key, old) -> {
              if (old == null || old.windowStart != windowStart) {
                return new Window(windowStart);
              }
              old.count.incrementAndGet();
              return old;
            });
    return window.count.get() <= maxPerHour;
  }
}

package com.aievolution.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * W29 #4：SSRF 出向门禁行为锁定——内网/保留网段拒连、例外清单、fail-closed。
 *
 * <p>解析器全桩化（时钟注入先例）：字面 IP 经 {@code InetAddress.getByName} 不触 DNS， 主机名解析由桩返回预设地址，测试不依赖网络。
 */
class EgressUrlGuardTest {

  /** 桩解析器：主机名 → 预设地址；字面 IP 走真实解析（不触 DNS） */
  private static EgressUrlGuard guardWith(EgressUrlGuard.HostResolver resolver) {
    return new EgressUrlGuard(Set.of(), resolver);
  }

  private static EgressUrlGuard.HostResolver literalIps() {
    return InetAddress::getAllByName; // 字面 IP 不触 DNS
  }

  @Test
  void publicHostIsAllowed() throws Exception {
    EgressUrlGuard guard =
        guardWith(host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});

    assertThat(guard.check("https://example.com/news")).isEmpty();
  }

  @Test
  void loopbackLiteralIsRejected() {
    EgressUrlGuard guard = guardWith(literalIps());

    assertThat(guard.check("http://127.0.0.1:8080/admin"))
        .hasValueSatisfying(reason -> assertThat(reason).contains("内网/保留网段"));
  }

  @Test
  void rfc1918LiteralsAreRejected() {
    EgressUrlGuard guard = guardWith(literalIps());

    assertThat(guard.check("http://10.0.0.1/")).isPresent();
    assertThat(guard.check("http://172.16.0.1/")).isPresent();
    assertThat(guard.check("http://192.168.1.1/")).isPresent();
  }

  @Test
  void linkLocalAndAnyLocalAreRejected() {
    EgressUrlGuard guard = guardWith(literalIps());

    assertThat(guard.check("http://169.254.169.254/latest/meta-data")).isPresent(); // 云元数据端点
    assertThat(guard.check("http://0.0.0.0/")).isPresent();
  }

  @Test
  void hostnameResolvingToPrivateAddressIsRejected() throws Exception {
    // 字面不是内网但解析到内网：DNS 面也要拦
    EgressUrlGuard guard =
        guardWith(host -> new InetAddress[] {InetAddress.getByName("192.168.0.1")});

    assertThat(guard.check("http://internal.example.com/")).isPresent();
  }

  @Test
  void mixedResolutionPublicAndPrivateIsRejected() throws Exception {
    // 任一地址落入内网即拒（round-robin DNS 不能成为漏网口）
    EgressUrlGuard guard =
        guardWith(
            host ->
                new InetAddress[] {
                  InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")
                });

    assertThat(guard.check("http://mixed.example.com/")).isPresent();
  }

  @Test
  void unresolvableHostFailsClosed() {
    EgressUrlGuard guard =
        guardWith(
            host -> {
              throw new UnknownHostException(host);
            });

    assertThat(guard.check("http://no-such-host.invalid/"))
        .hasValueSatisfying(reason -> assertThat(reason).contains("fail-closed"));
  }

  @Test
  void nonHttpSchemesAreRejected() {
    EgressUrlGuard guard = guardWith(literalIps());

    assertThat(guard.check("file:///etc/passwd")).isPresent();
    assertThat(guard.check("ftp://example.com/")).isPresent();
  }

  @Test
  void allowlistExemptsExplicitHost() throws Exception {
    EgressUrlGuard guard =
        new EgressUrlGuard(
            Set.of("internal.example.com"),
            host -> new InetAddress[] {InetAddress.getByName("192.168.0.1")});

    assertThat(guard.check("http://internal.example.com/report")).isEmpty();
  }
}

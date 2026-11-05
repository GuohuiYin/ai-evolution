package com.aievolution.mcp;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出向 URL 门禁（W29 #4，SSRF 防线）：MCP fetch 的目标 URL 由模型生成，可能指向内网/保留网段。
 *
 * <p>策略：<b>黑名单制</b>（取证裁决——fetch 工具的用途是开放公网抓取，反向白名单会杀死工具价值； 黑名单拦内网保留面，公网自由通行）：
 *
 * <ul>
 *   <li>协议只放 http/https（file/ftp 等直接拒）
 *   <li>主机解析出的任一地址落入 loopback / link-local / site-local（RFC1918）/ any-local / 组播 → 拒
 *   <li>解析失败 fail-closed（宁可误拒，不放未知）
 *   <li>配置化例外清单 {@code ai.mcp.fetch.egress-allowed-hosts}（精确主机名，默认空）
 * </ul>
 *
 * <p>已知边界（如实声明）：真实抓取由对方 fetch 服务自行解析 DNS，本守卫的解析与其存在 TOCTOU 窗口（DNS rebinding 可绕过）——单 Owner
 * 本地场景接受；生产级处置是 egress 代理统一出向， 登记在案不夸大防线强度。
 *
 * <p>解析器构造器注入（时钟注入先例）：单测用桩解析器，不碰真实 DNS。
 */
public class EgressUrlGuard {

  /** 主机名解析策略（生产=InetAddress 系统解析；测试=桩） */
  @FunctionalInterface
  public interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final Set<String> allowedHosts;
  private final HostResolver resolver;

  public EgressUrlGuard(Set<String> allowedHosts, HostResolver resolver) {
    this.allowedHosts =
        allowedHosts == null
            ? Set.of()
            : allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    this.resolver = resolver;
  }

  /**
   * 检查目标 URL 是否允许出向。
   *
   * @return 拒绝原因（喂回模型的观察文本后缀）；空 = 放行
   */
  public Optional<String> check(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return Optional.of("URL 格式非法");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      return Optional.of("仅允许 http/https 协议（SSRF 防线，W29 #4）");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return Optional.of("URL 缺少主机名");
    }
    if (allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
      return Optional.empty(); // 显式例外清单：配置即审计
    }
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException | SecurityException e) {
      return Optional.of("目标域名无法解析（fail-closed，宁可误拒不放未知）");
    }
    for (InetAddress address : addresses) {
      if (address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isAnyLocalAddress()
          || address.isMulticastAddress()) {
        return Optional.of("目标地址落入内网/保留网段（%s，SSRF 防线，W29 #4）".formatted(address.getHostAddress()));
      }
    }
    return Optional.empty();
  }
}

package com.aievolution.infra;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

/**
 * W30 #2 测试基建：进程内 RSA 密钥对 + JDK 内嵌 HTTP 服务器暴露 JWKS 端点（随机端口）， 测试类经 {@code @DynamicPropertySource} 把
 * {@code jwk-set-uri} 指过来——资源服务器真验签，不打桩。
 *
 * <p>用法：
 *
 * <pre>
 * &#64;DynamicPropertySource
 * static void jwtProperties(DynamicPropertyRegistry registry) {
 *   registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwtSupport::jwkSetUri);
 * }
 * </pre>
 *
 * <p>单例懒加载：密钥对与端口在 JVM 内全局唯一，各测试类共享；服务器随 JVM 退出经 shutdown hook 关闭。
 */
public final class TestJwtSupport {

  private static final TestJwtSupport INSTANCE = new TestJwtSupport();

  private final RSAKey signingKey;
  private final String jwkSetUri;

  private TestJwtSupport() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var keyPair = generator.generateKeyPair();
      signingKey =
          new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
              .privateKey((RSAPrivateKey) keyPair.getPrivate())
              .keyID("test-jwt-key")
              .build();
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      byte[] jwks =
          new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/jwks",
          exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
          });
      server.start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
      jwkSetUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA 密钥生成失败", e);
    } catch (IOException e) {
      throw new UncheckedIOException("内嵌 JWKS 端点启动失败", e);
    }
  }

  /** 内嵌 JWKS 端点地址（供 @DynamicPropertySource 注入 jwk-set-uri） */
  public static String jwkSetUri() {
    return INSTANCE.jwkSetUri;
  }

  /**
   * 签发 RS256 JWT（1 小时有效）。
   *
   * @param sub 主体（限流分桶键 / 审计身份）
   * @param role 人通道角色声明（如 "admin"/"writer"/"reader"），不需要时传 null
   * @param scope 机器通道 scope 声明（空格分隔，如 "writer"），不需要时传 null
   */
  public static String issueToken(String sub, String role, String scope) {
    try {
      JWTClaimsSet.Builder claims =
          new JWTClaimsSet.Builder()
              .subject(sub)
              .issuer("http://test-auth-center")
              .issueTime(new Date())
              .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
      if (role != null) {
        claims.claim("role", role);
      }
      if (scope != null) {
        claims.claim("scope", scope);
      }
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .keyID(INSTANCE.signingKey.getKeyID())
                  .build(),
              claims.build());
      jwt.sign(new RSASSASigner(INSTANCE.signingKey));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("测试 JWT 签发失败", e);
    }
  }
}

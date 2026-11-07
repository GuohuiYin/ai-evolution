package com.aievolution.authcenter.protocol;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 签名密钥管理（W30 #4 初始密钥管理；轮转规程顺延 W31）。
 *
 * <ul>
 *   <li><b>持久通道</b>：{@code AUTH_JWT_PRIVATE_KEY} 注入 RSA 私钥（PKCS8 PEM；env 里换行难写， 接受字面 {@code \n}
 *       转义，装入前还原）——重启不换钥，已签发 JWT 持续有效；部署态经 Secret 注入（A5），公钥由私钥（CRT 参数）推导，JWKS 端点只暴露公钥
 *   <li><b>缺省（本地开发）</b>：每启动临时生成 + WARN 有声化——重启即换钥，旧 token 全失效， 客户端重新登录/取 token 即恢复；fail-open
 *       仅限无持久密钥时（开发姿态）， PEM 提供但解析失败则启动 fail-closed（密钥错了宁可起不来）
 * </ul>
 */
@Configuration
class SigningKeyConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SigningKeyConfiguration.class);

  @Bean
  JWKSource<SecurityContext> jwkSource(
      @Value("${auth.jwt.private-key-pem:}") String privateKeyPem) {
    RSAKey key =
        (privateKeyPem == null || privateKeyPem.isBlank()) ? ephemeral() : load(privateKeyPem);
    return new ImmutableJWKSet<>(new JWKSet(key));
  }

  private static RSAKey ephemeral() {
    log.warn(
        "签名密钥为本次启动临时生成：重启即换钥，全部已签发 JWT 失效（客户端重新登录/取 token 即可恢复）；"
            + "持久化请经 AUTH_JWT_PRIVATE_KEY 注入 PKCS8 PEM（W30 #4，轮转规程 W31）");
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var keyPair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .privateKey(keyPair.getPrivate())
          .keyID("ephemeral-" + UUID.randomUUID())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("临时签名密钥生成失败", e);
    }
  }

  private static RSAKey load(String pem) {
    try {
      String normalized = pem.replace("\\n", "\n");
      String base64 = normalized.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
      KeyFactory factory = KeyFactory.getInstance("RSA");
      RSAPrivateCrtKey privateKey =
          (RSAPrivateCrtKey)
              factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
      RSAPublicKey publicKey =
          (RSAPublicKey)
              factory.generatePublic(
                  new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
      return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("env-injected").build();
    } catch (Exception e) {
      throw new IllegalStateException(
          "AUTH_JWT_PRIVATE_KEY 解析失败：需 PKCS8 PEM 格式 RSA 私钥（换行可用字面 \\n 转义）", e);
    }
  }
}

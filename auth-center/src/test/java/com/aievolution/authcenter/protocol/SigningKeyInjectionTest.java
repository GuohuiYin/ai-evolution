package com.aievolution.authcenter.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * W30 #4 初始密钥管理锁定：{@code AUTH_JWT_PRIVATE_KEY} 注入 PKCS8 PEM 时， JWKS 端点暴露其派生公钥（kid 固定
 * env-injected）——重启不换钥的持久通道成立。缺省临时生成路径由其余测试（默认上下文）隐式覆盖。
 */
class SigningKeyInjectionTest extends AbstractAuthCenterTest {

  @DynamicPropertySource
  static void signingKeyProps(DynamicPropertyRegistry registry) throws Exception {
    // 运行时生成测试密钥对，PEM 换行以字面 \n 转义注入（模拟 env 单行情态）
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String base64 =
        Base64.getMimeEncoder()
            .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    String pem =
        ("-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n")
            .replace("\n", "\\n");
    registry.add("auth.jwt.private-key-pem", () -> pem);
  }

  @Test
  void jwksExposesInjectedKey() throws Exception {
    var response = get(newBrowser(), base() + "/oauth2/jwks");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"kid\":\"env-injected\"");
  }
}

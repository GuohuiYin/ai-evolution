package com.aievolution.authcenter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 全上下文测试基座（W30 #1 步骤 3 起，identity 域引入 DataSource，所有全链路测试共用）： Testcontainers
 * MySQL（mysql:8.4，与本地容器同版本）+ admin 种子哈希（argon2 运行时现算， 测试口令固定 {@link #TEST_ADMIN_PASSWORD}）。
 *
 * <p>实踩坑：容器用「单例模式」（static 块手动 start，Ryuk 进程结束回收）而非 {@code @Testcontainers
 * + @Container}——后者按「每个测试类」停起容器，重启后随机端口变更， 而 Spring 上下文缓存里的 DataSource 仍指旧端口：健康检查的 db 指示器拿不到连接， 等满
 * Hikari 30 秒超时返回 503（首个用库的类能过、后续类全挂）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractAuthCenterTest {

  protected static final String TEST_ADMIN_PASSWORD = "test-admin-password";

  static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  static {
    MYSQL.start();
  }

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add(
        "auth.identity.admin-password-hash",
        () ->
            "{argon2}"
                + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
                    .encode(TEST_ADMIN_PASSWORD));
    registry.add(
        "spring.security.oauth2.authorizationserver.client.mcp-client.registration.client-secret",
        () -> "{noop}test-secret");
  }

  @Value("${local.server.port}")
  protected int port;
}

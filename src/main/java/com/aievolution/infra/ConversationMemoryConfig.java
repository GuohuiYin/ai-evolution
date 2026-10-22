package com.aievolution.infra;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 会话记忆原语（W10 #0a-2 L1；W21 #1③ L2）：{@link MessageWindowChatMemory} + 滑动窗口， 会话身份经 {@code
 * ChatMemory.CONVERSATION_ID} 参数按请求隔离；仓储实现由 {@code ai.chat.memory.store} 旗标选择 （inmemory 缺省 / jdbc
 * 持久化）。
 *
 * <p>刻意不做全局挂载：分析服务与 eval judge 也用 ChatClient，共享会话记忆会互相污染；
 * 记忆只由对话域（RagChatService/AgentChatService）在构造器定点挂载。
 *
 * <p>L2 落库 MySQL（容器 ai-evolution-mysql）：重启不失忆、多副本同源，五级路线中"外部 DB" 档已就位（docs/plans/m3/README.md）。
 */
@Configuration
public class ConversationMemoryConfig {

  /** L1 内存版（缺省）：重启即失忆。 */
  @Bean
  @ConditionalOnProperty(
      name = "ai.chat.memory.store",
      havingValue = "inmemory",
      matchIfMissing = true)
  public ChatMemoryRepository inMemoryChatMemoryRepository() {
    return new InMemoryChatMemoryRepository();
  }

  /**
   * L2 持久化版（W21 #1③）：MySQL（缺省连接串指向容器 ai-evolution-mysql），重启记忆仍在。
   * 建表脚本按数据库产品名从模块自带脚本中选取（schema-mysql.sql 等，IF NOT EXISTS 幂等， 每次启动重放无副作用）。DataSource 手工装配而非走 Boot
   * 自动配置—— 后者已在 application.yml 显式排除，保证 inmemory 缺省态零 DataSource 副作用。方言同样按产品名自动识别， 换库只动
   * jdbc-url/凭据，此处零改动。
   */
  @Bean
  @ConditionalOnProperty(name = "ai.chat.memory.store", havingValue = "jdbc")
  public ChatMemoryRepository jdbcChatMemoryRepository(
      @Value(
              "${ai.chat.memory.jdbc-url:jdbc:mysql://localhost:3306/ai_evolution?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai}")
          String jdbcUrl,
      @Value("${ai.chat.memory.jdbc-username:root}") String username,
      @Value("${ai.chat.memory.jdbc-password:}") String password)
      throws SQLException {
    // DriverManagerDataSource 而非具体数据库类：驱动是 runtime 依赖（ServiceLoader 自动注册），
    // 主代码不编译期绑定，换库只动配置
    DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
    try (Connection connection = dataSource.getConnection()) {
      String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
      ClassPathResource schema =
          new ClassPathResource(
              "org/springframework/ai/chat/memory/repository/jdbc/schema-" + product + ".sql");
      if (!schema.exists()) {
        throw new IllegalStateException("spring-ai 未内置 " + product + " 的会话记忆建表脚本，请改用受支持的方言或手工建表");
      }
      ScriptUtils.executeSqlScript(connection, schema);
    }
    return JdbcChatMemoryRepository.builder()
        .jdbcTemplate(new JdbcTemplate(dataSource))
        .dialect(JdbcChatMemoryRepositoryDialect.from(dataSource))
        .build();
  }

  @Bean
  public ChatMemory chatMemory(
      ChatMemoryRepository repository, @Value("${ai.chat.memory.window:20}") int window) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(window)
        .build();
  }
}

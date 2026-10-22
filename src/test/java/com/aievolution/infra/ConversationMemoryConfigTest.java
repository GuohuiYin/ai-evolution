package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** W21 #1③：会话记忆 L2——旗标装配（inmemory 缺省 / jdbc）+ 文件库重启后记忆仍在。 */
class ConversationMemoryConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ConversationMemoryConfig.class);

  @Test
  void 缺省装配内存版仓储() {
    runner.run(
        context -> {
          assertThat(context.getBean(ChatMemoryRepository.class))
              .isInstanceOf(InMemoryChatMemoryRepository.class);
          assertThat(context.getBean(ChatMemory.class)).isNotNull();
        });
  }

  @Test
  void jdbc模式文件库重启后记忆仍在(@TempDir Path dir) {
    String jdbcUrl = "jdbc:h2:file:" + dir.resolve("chat-memory");
    String[] props = {"ai.chat.memory.store=jdbc", "ai.chat.memory.jdbc-url=" + jdbcUrl};

    // 第一次启动：写入一轮对话
    runner
        .withPropertyValues(props)
        .run(
            context -> {
              ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
              assertThat(repository).isInstanceOf(JdbcChatMemoryRepository.class);
              repository.saveAll(
                  "c1", List.of(new UserMessage("茅台 2024 年营收多少"), new AssistantMessage("1741 亿元")));
            }); // context 关闭 ≈ 进程重启

    // 第二次启动：同一库文件，记忆可读回
    runner
        .withPropertyValues(props)
        .run(
            context -> {
              List<Message> messages =
                  context.getBean(ChatMemoryRepository.class).findByConversationId("c1");
              assertThat(messages).hasSize(2);
              assertThat(messages.get(0).getText()).contains("茅台 2024 年营收多少");
              assertThat(messages.get(1).getText()).contains("1741 亿元");
            });
  }

  @Test
  void jdbc模式建表脚本幂等可重复启动(@TempDir Path dir) {
    String jdbcUrl = "jdbc:h2:file:" + dir.resolve("chat-memory");
    String[] props = {"ai.chat.memory.store=jdbc", "ai.chat.memory.jdbc-url=" + jdbcUrl};
    // 同一库连起两次（第二遍重放 schema-h2.sql），IF NOT EXISTS 不炸即通过
    runner.withPropertyValues(props).run(context -> assertThat(context).hasNotFailed());
    runner.withPropertyValues(props).run(context -> assertThat(context).hasNotFailed());
  }
}

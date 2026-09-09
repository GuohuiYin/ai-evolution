package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * W8-6：prompt/response 日志可观测。验证 {@link SimpleLoggerAdvisor} 挂载后， 模型调用的输入 prompt 与返回 response
 * 确实落到日志。
 */
class PromptLoggingAdvisorTest {

  private static final String LOGGER_NAME = SimpleLoggerAdvisor.class.getName();

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;
  private Level originalLevel;

  @BeforeEach
  void attachLogCapture() {
    logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachLogCapture() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
  }

  private ChatModel fakeChatModel() {
    ChatModel chatModel = mock(ChatModel.class);
    when(chatModel.getOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("模型应答：营收1741亿元")))));
    return chatModel;
  }

  private List<String> capturedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void logsPromptAndResponseWhenAdvisorMounted() {
    ChatClient chatClient =
        ChatClient.builder(fakeChatModel()).defaultAdvisors(new SimpleLoggerAdvisor()).build();

    String reply = chatClient.prompt().user("600519 营收多少").call().content();

    assertThat(reply).isEqualTo("模型应答：营收1741亿元");
    assertThat(capturedMessages())
        .anySatisfy(m -> assertThat(m).contains("600519 营收多少"))
        .anySatisfy(m -> assertThat(m).contains("模型应答：营收1741亿元"));
  }

  @Test
  void noPromptResponseLogsWithoutAdvisor() {
    ChatClient chatClient = ChatClient.builder(fakeChatModel()).build();

    chatClient.prompt().user("600519 营收多少").call().content();

    // 未挂 Advisor 时，该 logger 下不应有任何 prompt/response 记录（对照组）
    assertThat(capturedMessages()).isEmpty();
  }
}

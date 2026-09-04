package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PromptLibraryTest {

  private final PromptLibrary library = new PromptLibrary();

  @Test
  void loadsVersionedPromptFromClasspath() {
    String template = library.get("rag-chat-v1");

    assertThat(template).contains("{context}").contains("{question}");
  }

  @Test
  void cachesLoadedPrompt() {
    String first = library.get("rag-chat-v1");
    String second = library.get("rag-chat-v1");

    assertThat(second).isSameAs(first);
  }

  @Test
  void failsFastWithClearMessageForUnknownPrompt() {
    assertThatThrownBy(() -> library.get("no-such-prompt"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no-such-prompt");
  }
}

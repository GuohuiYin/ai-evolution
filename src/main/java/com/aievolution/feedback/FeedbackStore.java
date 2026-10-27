package com.aievolution.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * badcase 库（W24 #3 最小环）：JSON 数组文件持久化，量小低频，整读整写即可。 不依赖 DataSource——会话记忆 inmemory/jdbc
 * 两态下反馈回路都可用（记忆仓储旗标与反馈 登记是两条独立关注点）。文件默认 {@code data/feedback.jsonl}（gitignored）， 裁定产物（golden 候选）由
 * scripts/badcase_review.py 写入 docs/ 走版本控制留痕。
 */
@Component
public class FeedbackStore {

  private final Path file;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FeedbackStore(@Value("${ai.feedback.file:data/feedback.jsonl}") String file) {
    this.file = Path.of(file);
  }

  public synchronized void append(FeedbackEntry entry) {
    List<FeedbackEntry> all = list();
    all.add(entry);
    save(all);
  }

  public synchronized List<FeedbackEntry> list() {
    if (!Files.exists(file)) {
      return new ArrayList<>();
    }
    try {
      return new ArrayList<>(
          objectMapper.readValue(file.toFile(), new TypeReference<List<FeedbackEntry>>() {}));
    } catch (IOException e) {
      throw new UncheckedIOException("badcase 库读取失败: " + file, e);
    }
  }

  /** 人工裁定写回（approve/reject 由脚本经本方法同款语义操作文件；服务端保留以便未来管理端点）。 */
  public synchronized Optional<FeedbackEntry> updateStatus(String id, String status) {
    List<FeedbackEntry> all = list();
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).id().equals(id)) {
        FeedbackEntry updated = all.get(i).withStatus(status);
        all.set(i, updated);
        save(all);
        return Optional.of(updated);
      }
    }
    return Optional.empty();
  }

  private void save(List<FeedbackEntry> all) {
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), all);
    } catch (IOException e) {
      throw new UncheckedIOException("badcase 库写入失败: " + file, e);
    }
  }
}

package com.aievolution.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** badcase 库存储单测（W24 #3）：登记/列举/裁定状态流转 + 空库容错。 */
class FeedbackStoreTest {

  @TempDir Path tempDir;

  private FeedbackStore store() {
    return new FeedbackStore(tempDir.resolve("feedback.jsonl").toString());
  }

  private FeedbackEntry entry(String id, String status) {
    return new FeedbackEntry(
        id, "conv-1", "down", "答非所问", "问", "答", status, "2026-10-27T13:00:00Z");
  }

  @Test
  void appendThenListRoundTrips() {
    FeedbackStore store = store();
    store.append(entry("a1b2c3d4", FeedbackEntry.STATUS_PENDING_REVIEW));
    store.append(entry("e5f6a7b8", FeedbackEntry.STATUS_NOTED));

    List<FeedbackEntry> all = store.list();
    assertThat(all).hasSize(2);
    assertThat(all.get(0).id()).isEqualTo("a1b2c3d4");
    assertThat(all.get(0).comment()).isEqualTo("答非所问");
    // 换实例再读——验证真的落盘了而不是内存缓存
    assertThat(store().list()).hasSize(2);
  }

  @Test
  void listOnMissingFileReturnsEmpty() {
    assertThat(store().list()).isEmpty();
  }

  @Test
  void updateStatusFlipsOnlyTarget() {
    FeedbackStore store = store();
    store.append(entry("a1b2c3d4", FeedbackEntry.STATUS_PENDING_REVIEW));
    store.append(entry("e5f6a7b8", FeedbackEntry.STATUS_PENDING_REVIEW));

    assertThat(store.updateStatus("a1b2c3d4", FeedbackEntry.STATUS_APPROVED)).isPresent();
    assertThat(store.updateStatus("not-exist", FeedbackEntry.STATUS_APPROVED)).isEmpty();

    List<FeedbackEntry> all = store().list();
    assertThat(all.get(0).status()).isEqualTo(FeedbackEntry.STATUS_APPROVED);
    assertThat(all.get(1).status()).isEqualTo(FeedbackEntry.STATUS_PENDING_REVIEW);
  }
}

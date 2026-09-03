package com.aievolution.rag;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;

/**
 * 测试替身（Test Double）：确定性的"字符二元组哈希"向量，用于在不调用云端 Embedding API 的 前提下验证向量库存取链路。共享子串越多的文本向量越相似，足以支撑相似度断言。
 * 真实语义向量由 SiliconFlow bge-m3 提供（W3-Step2 接入），接口不变。
 */
class TinyHashEmbeddingModel implements EmbeddingModel {

  static final int DIMENSIONS = 32;

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<Embedding> results =
        IntStream.range(0, request.getInstructions().size())
            .mapToObj(
                i ->
                    new Embedding(
                        embed(request.getInstructions().get(i)), i, EmbeddingResultMetadata.EMPTY))
            .toList();
    return new EmbeddingResponse(results);
  }

  @Override
  public float[] embed(String text) {
    float[] vector = new float[DIMENSIONS];
    for (int i = 0; i + 1 < text.length(); i++) {
      vector[Math.floorMod(text.substring(i, i + 2).hashCode(), DIMENSIONS)] += 1f;
    }
    double sumSquares = 0;
    for (float v : vector) {
      sumSquares += v * v;
    }
    double norm = Math.sqrt(sumSquares);
    if (norm > 0) {
      for (int i = 0; i < DIMENSIONS; i++) {
        vector[i] /= (float) norm;
      }
    }
    return vector;
  }

  @Override
  public float[] embed(Document document) {
    return embed(document.getText());
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }
}

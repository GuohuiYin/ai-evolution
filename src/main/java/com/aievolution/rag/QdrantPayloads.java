package com.aievolution.rag;

import io.qdrant.client.grpc.JsonWithInt.Value;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant gRPC 装配/拆解转换小工具（W16 #2：matchtext 与 BM25 两条 sparse 路共用）。 payload
 * 只取本工程元数据用到的标量种类；数组装箱服务于稀疏向量 indices/values 组装。
 */
final class QdrantPayloads {

  private QdrantPayloads() {}

  /** payload protobuf Value → Java 原生类型。 */
  static Map<String, Object> toPlainMap(Map<String, Value> payload) {
    Map<String, Object> out = new HashMap<>();
    payload.forEach(
        (k, v) -> {
          switch (v.getKindCase()) {
            case STRING_VALUE -> out.put(k, v.getStringValue());
            case INTEGER_VALUE -> out.put(k, v.getIntegerValue());
            case DOUBLE_VALUE -> out.put(k, v.getDoubleValue());
            case BOOL_VALUE -> out.put(k, v.getBoolValue());
            default -> {
              /* NULL_/LIST_/STRUCT_VALUE 本工程元数据不涉及，跳过 */
            }
          }
        });
    return out;
  }

  /** 稀疏向量 indices 装箱（int[] → List<Integer>）。 */
  static List<Integer> toIntList(int[] values) {
    return Arrays.stream(values).boxed().toList();
  }

  /** 稀疏向量 values 装箱（float[] → List<Float>）。 */
  static List<Float> toFloatList(float[] values) {
    Float[] boxed = new Float[values.length];
    for (int i = 0; i < values.length; i++) {
      boxed[i] = values[i];
    }
    return List.of(boxed);
  }
}

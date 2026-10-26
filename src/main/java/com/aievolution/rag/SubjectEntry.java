package com.aievolution.rag;

import java.util.List;

/**
 * 在库主体词典条目（W23 #2）：规范名 + 别名 + 股票代码。词典经 {@code ai.rag.diversity.subjects}
 * 外置配置（A11），语料轮换时同步维护——词典与在库语料同源，新增公司入库即加条目。
 */
public record SubjectEntry(String name, List<String> aliases, String code) {}

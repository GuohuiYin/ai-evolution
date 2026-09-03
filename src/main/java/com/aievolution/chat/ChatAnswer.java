package com.aievolution.chat;

import java.util.List;

/** 对话结果：回复正文 + 引用来源（金融红线三"数字必须可溯源"的载体）。 */
public record ChatAnswer(String reply, List<SourceDocument> sources) {}

package com.aievolution.skill;

import java.util.List;

/**
 * 技能（W22 #2，设计 §2.1）：「指令 + 资源」的可复用包—— name/description/triggers 元数据 + 正文。
 * 渐进披露：元数据常驻上下文（一行），正文命中触发才注入。
 */
public record Skill(String name, String description, List<String> triggers, String body) {}

package com.aievolution.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {}

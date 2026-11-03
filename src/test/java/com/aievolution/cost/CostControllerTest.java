package com.aievolution.cost;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 预算运行时调整端点测试（W27 #4b）：调额生效并回显状态 / 非正值 400 / 状态端点回显当日调整留痕。 */
@WebMvcTest(CostController.class)
class CostControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TokenLedger ledger;
  @MockitoBean private DailyBudgetOverride budget;

  @Test
  void adjustBudgetTakesEffectAndEchoesState() throws Exception {
    when(budget.effectiveBudget()).thenReturn(8_000_000L);
    when(budget.configuredDefault()).thenReturn(200_000L);

    mockMvc
        .perform(
            post("/ai/cost/budget")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"budget\":8000000,\"reason\":\"撞线救场\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveBudget").value(8_000_000))
        .andExpect(jsonPath("$.configuredDefault").value(200_000));

    verify(budget).adjust(8_000_000L, "撞线救场");
  }

  @Test
  void nonPositiveBudgetIs400() throws Exception {
    mockMvc
        .perform(
            post("/ai/cost/budget")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"budget\":0}"))
        .andExpect(status().isBadRequest());

    verify(budget, never())
        .adjust(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void budgetStateEchoesActiveAdjustment() throws Exception {
    when(budget.effectiveBudget()).thenReturn(8_000_000L);
    when(budget.configuredDefault()).thenReturn(200_000L);
    when(budget.currentAdjustment())
        .thenReturn(
            new DailyBudgetOverride.Adjustment(
                LocalDate.of(2026, 11, 3),
                8_000_000L,
                "撞线救场",
                Instant.parse("2026-11-03T04:00:00Z")));

    mockMvc
        .perform(get("/ai/cost/budget"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.adjustment.budget").value(8_000_000))
        .andExpect(jsonPath("$.adjustment.reason").value("撞线救场"))
        .andExpect(jsonPath("$.adjustment.day").value("2026-11-03"));
  }
}

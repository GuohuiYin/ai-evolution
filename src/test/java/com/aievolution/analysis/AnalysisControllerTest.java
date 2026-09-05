package com.aievolution.analysis;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aievolution.compliance.Disclaimers;
import com.aievolution.prompt.InvalidPromptVersionException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StockAnalysisService analysisService;

  @Test
  void analyzeReturnsReportWithMandatoryDisclaimer() throws Exception {
    StockAnalysisReport report =
        new StockAnalysisReport("600519", "结论", List.of("亮点"), List.of("风险"), List.of("mock"));
    when(analysisService.analyze("600519", "v1")).thenReturn(report);

    // 红线 01：免责声明由服务端强制附带（A12：文案全项目单点定义）
    mockMvc
        .perform(post("/ai/analyze").param("code", "600519"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.disclaimer").value(Disclaimers.AI_GENERATED));
  }

  @Test
  void unknownPromptVersionIsClientError() throws Exception {
    when(analysisService.analyze("600519", "v9"))
        .thenThrow(new InvalidPromptVersionException("v9"));

    mockMvc
        .perform(post("/ai/analyze").param("code", "600519").param("promptVersion", "v9"))
        .andExpect(status().isBadRequest());
  }
}

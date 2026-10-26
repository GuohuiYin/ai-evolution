package com.aievolution.chat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aievolution.loop.temporal.ResearchPipelineWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HITL 审批入口单测（W23 #1）：状态查询 / 放行 / 驳回 / 非挂起冲突 / 未知 workflow 404 / 非法决定 400。 */
@WebMvcTest(ApprovalController.class)
class ApprovalControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WorkflowClient pipelineWorkflowClient;

  private ResearchPipelineWorkflow stub(String workflowId) {
    ResearchPipelineWorkflow workflow = org.mockito.Mockito.mock(ResearchPipelineWorkflow.class);
    when(this.pipelineWorkflowClient.newWorkflowStub(ResearchPipelineWorkflow.class, workflowId))
        .thenReturn(workflow);
    return workflow;
  }

  @Test
  void statusReportsPendingApproval() throws Exception {
    ResearchPipelineWorkflow workflow = stub("pipeline-t1");
    when(workflow.status()).thenReturn("awaiting-approval");
    when(workflow.pendingApproval()).thenReturn(true);

    mockMvc
        .perform(get("/api/approvals/pipeline-t1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("awaiting-approval"))
        .andExpect(jsonPath("$.pendingApproval").value(true));
  }

  @Test
  void approveSignalsWorkflow() throws Exception {
    ResearchPipelineWorkflow workflow = stub("pipeline-t2");
    when(workflow.pendingApproval()).thenReturn(true);

    mockMvc
        .perform(
            post("/api/approvals/pipeline-t2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\",\"comment\":\"口径已人工核实\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.decision").value("approve"));

    verify(workflow).approve("口径已人工核实");
    verify(workflow, never()).reject(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void rejectSignalsWorkflow() throws Exception {
    ResearchPipelineWorkflow workflow = stub("pipeline-t3");
    when(workflow.pendingApproval()).thenReturn(true);

    mockMvc
        .perform(
            post("/api/approvals/pipeline-t3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"reject\"}"))
        .andExpect(status().isAccepted());

    verify(workflow).reject("");
  }

  @Test
  void decideOnNonPendingWorkflowConflicts() throws Exception {
    ResearchPipelineWorkflow workflow = stub("pipeline-t4");
    when(workflow.pendingApproval()).thenReturn(false);

    mockMvc
        .perform(
            post("/api/approvals/pipeline-t4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\"}"))
        .andExpect(status().isConflict());

    verify(workflow, never()).approve(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void unknownWorkflowReturns404() throws Exception {
    ResearchPipelineWorkflow workflow = stub("pipeline-gone");
    io.temporal.api.common.v1.WorkflowExecution execution =
        io.temporal.api.common.v1.WorkflowExecution.newBuilder()
            .setWorkflowId("pipeline-gone")
            .setRunId("run-1")
            .build();
    when(workflow.status()).thenThrow(new WorkflowNotFoundException(execution, "not found", null));

    mockMvc.perform(get("/api/approvals/pipeline-gone")).andExpect(status().isNotFound());
  }

  @Test
  void invalidDecisionRejected400() throws Exception {
    stub("pipeline-t5");

    mockMvc
        .perform(
            post("/api/approvals/pipeline-t5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"maybe\"}"))
        .andExpect(status().isBadRequest());
  }
}

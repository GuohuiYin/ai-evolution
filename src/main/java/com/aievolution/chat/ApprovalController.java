package com.aievolution.chat;

import com.aievolution.loop.temporal.ResearchPipelineWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HITL 审批入口（W23 #1）：打回耗尽挂起的 workflow 在此收人工决定。
 *
 * <p>裁决①B 范围：唯一触发=「打回耗尽升级人工」。放行（approve）= 以审批人背书放出最后草稿； 驳回（reject）= 维持拒答；超时未决由 workflow 侧
 * fail-closed 拒答（缺席策略）。
 *
 * <p>仅 pipeline 引擎装配（HITL 断点只在三角色管线上）；inline/temporal 单循环无此端点。
 */
@RestController
@RequestMapping("/api/approvals")
@ConditionalOnProperty(name = "ai.loop.engine", havingValue = "pipeline")
public class ApprovalController {

  private final WorkflowClient pipelineWorkflowClient;

  public ApprovalController(WorkflowClient pipelineWorkflowClient) {
    this.pipelineWorkflowClient = pipelineWorkflowClient;
  }

  public record ApprovalRequest(
      @NotBlank @Pattern(regexp = "approve|reject", message = "decision 仅支持 approve/reject")
          String decision,
      String comment) {}

  /** 挂起状态查询：status=awaiting-approval 且 pendingApproval=true 即正等人工决定 */
  @GetMapping("/{workflowId}")
  public ResponseEntity<Map<String, Object>> status(@PathVariable String workflowId) {
    try {
      ResearchPipelineWorkflow workflow = stub(workflowId);
      return ResponseEntity.ok(
          Map.of(
              "workflowId", workflowId,
              "status", workflow.status(),
              "pendingApproval", workflow.pendingApproval()));
    } catch (WorkflowNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "workflow 不存在或已关闭: " + workflowId));
    }
  }

  /** 人工决定：approve=放行（审批人背书放出最后草稿）；reject=驳回（维持拒答） */
  @PostMapping("/{workflowId}")
  public ResponseEntity<Map<String, Object>> decide(
      @PathVariable String workflowId, @Valid @RequestBody ApprovalRequest request) {
    try {
      ResearchPipelineWorkflow workflow = stub(workflowId);
      if (!workflow.pendingApproval()) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "workflow 未处于挂起待审批状态: " + workflowId));
      }
      String comment = request.comment() == null ? "" : request.comment();
      if ("approve".equals(request.decision())) {
        workflow.approve(comment);
      } else {
        workflow.reject(comment);
      }
      return ResponseEntity.accepted()
          .body(Map.of("workflowId", workflowId, "decision", request.decision()));
    } catch (WorkflowNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "workflow 不存在或已关闭: " + workflowId));
    }
  }

  private ResearchPipelineWorkflow stub(String workflowId) {
    return this.pipelineWorkflowClient.newWorkflowStub(ResearchPipelineWorkflow.class, workflowId);
  }
}

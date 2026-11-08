package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全事件审计流单测（W31 #4）：401/403/429 三个产生点的事件真落「security-audit」流， 事件类型与关键字段（path/identity/subject）锁定。
 */
class SecurityAuditFlowTest {

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    Logger auditLogger = (Logger) LoggerFactory.getLogger(SecurityAudit.LOGGER_NAME);
    this.appender = new ListAppender<>();
    this.appender.start();
    auditLogger.addAppender(this.appender);
  }

  @AfterEach
  void detachAppender() {
    Logger auditLogger = (Logger) LoggerFactory.getLogger(SecurityAudit.LOGGER_NAME);
    auditLogger.detachAppender(this.appender);
    SecurityContextHolder.clearContext();
  }

  private List<String> events() {
    return this.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void authFailureAuditedOn401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ai/cost/summary");
    request.setRemoteAddr("203.0.113.9");

    new SecurityProblemDetailHandler()
        .commence(
            request, new MockHttpServletResponse(), new InsufficientAuthenticationException("x"));

    assertThat(events())
        .anySatisfy(
            e ->
                assertThat(e)
                    .contains("event=AUTH_FAILURE")
                    .contains("path=/ai/cost/summary")
                    .contains("client=203.0.113.9"));
  }

  @Test
  void authzDeniedAuditedOn403WithSubject() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("mcp-client", null, "ROLE_READER"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");

    new SecurityProblemDetailHandler()
        .handle(
            request,
            new MockHttpServletResponse(),
            new org.springframework.security.access.AccessDeniedException("denied"));

    assertThat(events())
        .anySatisfy(
            e ->
                assertThat(e)
                    .contains("event=AUTHZ_DENIED")
                    .contains("path=/ai/chat")
                    .contains("subject=mcp-client"));
  }

  @Test
  void rateLimitedAuditedOn429() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, () -> 1000L);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ai/cost/summary");
    request.setRemoteAddr("198.51.100.7");

    filter.doFilter(
        request, new MockHttpServletResponse(), new org.springframework.mock.web.MockFilterChain());
    filter.doFilter(
        request, new MockHttpServletResponse(), new org.springframework.mock.web.MockFilterChain());

    assertThat(events())
        .anySatisfy(
            e -> assertThat(e).contains("event=RATE_LIMITED").contains("identity=ip:198.51.100.7"));
  }
}

package com.aievolution.authcenter.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 用户注册入口（W30 #6，设计见 docs/security/w30-registration-design.md）。
 *
 * <ul>
 *   <li>开放但限流：每 IP 每小时 N 次（{@link RegistrationRateLimiter}），防批量注册
 *   <li>注册即 PENDING（默认拒绝登录）+ 最小角色 READER；admin 只能来自 env 种子，此处产不出
 *   <li>明文不过夜：入参即 argon2 哈希（复用全局 PasswordEncoder），不落日志不落库
 *   <li>防用户名枚举：成功 / 占用 / 撞键竞争同一文案，不透露用户名是否已存在
 * </ul>
 */
@Controller
class RegistrationController {

  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");

  /** 枚举防护：成功与「用户名不可用」同文案 */
  private static final String GENERIC_REPLY = "注册请求已提交，待管理员审批通过后生效";

  private final UserAccountRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final RegistrationRateLimiter rateLimiter;

  RegistrationController(
      UserAccountRepository repository,
      PasswordEncoder passwordEncoder,
      RegistrationRateLimiter rateLimiter) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String form(CsrfToken csrf) {
    return page(
        """
        <h1>注册 AI Evolution 账号</h1>
        <form method="post" action="/register">
          <input type="hidden" name="%s" value="%s">
          <p><label>用户名（3-32 位字母/数字/下划线/连字符）：<input name="username" required minlength="3" maxlength="32"></label></p>
          <p><label>密码（至少 8 位）：<input name="password" type="password" required minlength="8"></label></p>
          <button type="submit">注册</button>
        </form>"""
            .formatted(csrf.getParameterName(), csrf.getToken()),
        null);
  }

  @PostMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  ResponseEntity<String> register(
      @RequestParam String username, @RequestParam String password, HttpServletRequest request) {
    if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(page(null, "注册请求过于频繁，请一小时后再试"));
    }
    if (!USERNAME_PATTERN.matcher(username).matches() || password.length() < 8) {
      return ResponseEntity.badRequest().body(page(null, "用户名需 3-32 位字母/数字/下划线/连字符，密码至少 8 位"));
    }
    try {
      if (repository.findByUsername(username).isEmpty()) {
        repository.insert(
            username,
            passwordEncoder.encode(password),
            UserAccount.Role.READER,
            UserAccount.Status.PENDING);
      }
    } catch (DuplicateKeyException e) {
      // 并发撞唯一键：与占用同文案，同样不透露
    }
    return ResponseEntity.ok(page(null, GENERIC_REPLY));
  }

  private static String page(String body, String notice) {
    return """
        <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>注册 · AI Evolution</title></head>
        <body style="font-family:sans-serif;max-width:420px;margin:48px auto">
        %s%s
        </body></html>"""
        .formatted(
            notice != null ? "<p style=\"color:#555\">" + notice + "</p>" : "",
            body != null ? body : "<p><a href=\"/login\">去登录</a></p>");
  }
}

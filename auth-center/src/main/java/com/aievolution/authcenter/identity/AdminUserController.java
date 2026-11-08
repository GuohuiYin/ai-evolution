package com.aievolution.authcenter.identity;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 用户审批面（W30 #6）：admin 会话（formLogin，链 2 {@code hasRole("ADMIN")} 保护） 查看 pending 清单并逐条激活。
 *
 * <p>设计取舍（草案裁决）：审批动作本体是人在浏览器点一下，会话形态最直接， 不为中心引入第二套资源服务器装配；JWT admin API 留触发条件（自动化审批诉求出现再做）。 admin
 * 只能来自 env 种子——本面不提供任何角色变更入口，激活只是把 PENDING 翻 ACTIVE。
 */
@Controller
class AdminUserController {

  private final UserAccountRepository repository;

  AdminUserController(UserAccountRepository repository) {
    this.repository = repository;
  }

  @GetMapping(value = "/admin/users", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String pendingList(CsrfToken csrf) {
    var pending = repository.findByStatus(UserAccount.Status.PENDING);
    StringBuilder rows = new StringBuilder();
    for (UserAccount account : pending) {
      rows.append(
          """
          <tr><td>%s</td><td>%s</td><td>
            <form method="post" action="/admin/users/%s/activate" style="margin:0">
              <input type="hidden" name="%s" value="%s">
              <button type="submit">激活</button>
            </form>
          </td></tr>"""
              .formatted(
                  account.username(),
                  account.createdAt(),
                  account.username(),
                  csrf.getParameterName(),
                  csrf.getToken()));
    }
    String table =
        pending.isEmpty()
            ? "<p>没有待审批的注册申请。</p>"
            : "<table border=\"1\" cellpadding=\"6\"><tr><th>用户名</th><th>注册时间</th><th>操作</th></tr>"
                + rows
                + "</table>";
    return """
        <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>注册审批 · AI Evolution</title></head>
        <body style="font-family:sans-serif;max-width:640px;margin:48px auto">
        <h1>注册审批</h1>
        %s
        </body></html>"""
        .formatted(table);
  }

  @PostMapping("/admin/users/{username}/activate")
  ResponseEntity<Void> activate(@PathVariable String username) {
    boolean updated = repository.updateStatus(username, UserAccount.Status.ACTIVE);
    // 激活后回清单页；用户名不存在 = 404（审批面是管理工具，不做枚举防护）
    return updated
        ? ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
            .header("Location", "/admin/users")
            .build()
        : ResponseEntity.notFound().build();
  }
}

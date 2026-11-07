package com.aievolution.chat;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 登录回调路由（W30 #3）：授权码+PKCE 的 redirect-uri 落点（{@code /callback}， 与认证中心客户端注册 yml 对齐——改动必须前后端同步）。
 *
 * <p>转发到静态页 {@code callback.html}：URL 干净（不带扩展名）且页面逻辑保持静态资源形态； 回调 code 的换取在浏览器侧完成（PKCE
 * 精髓：code_verifier 只在浏览器，服务端不经手）。
 */
@Controller
public class LoginCallbackController {

  @GetMapping("/callback")
  String callback() {
    return "forward:/callback.html";
  }
}

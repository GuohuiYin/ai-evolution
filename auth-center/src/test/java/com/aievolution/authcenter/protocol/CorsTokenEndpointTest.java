package com.aievolution.authcenter.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * W30 #3：对话页 SPA 跨域换 token 的 CORS 锁定——白名单 origin 预检放行（回 Allow-Origin）， 未登记 origin 拒绝（无 Allow-Origin
 * 头）。令牌端点 XHR 依赖此行为，删了对话页登录即断。
 */
class CorsTokenEndpointTest extends AbstractAuthCenterTest {

  private HttpResponse<String> preflight(String origin) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void preflightFromSpaOriginIsPermitted() throws Exception {
    HttpResponse<String> response = preflight("http://localhost:18080");

    assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
        .hasValue("http://localhost:18080");
  }

  @Test
  void preflightFromUnknownOriginIsDenied() throws Exception {
    HttpResponse<String> response = preflight("http://evil.example");

    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
}

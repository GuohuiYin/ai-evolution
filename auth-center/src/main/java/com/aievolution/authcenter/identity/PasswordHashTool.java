package com.aievolution.authcenter.identity;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * 种子密码哈希工具：本地跑一次，把输出整串（含 {argon2} 前缀）贴进 .env 的 AUTH_ADMIN_PASSWORD_HASH——密码明文不过夜（Owner 裁决候选 B）。
 *
 * <p>用法（注意 shell 历史，建议 `read -s PWD_RAW` 读入再传）：
 *
 * <pre>./mvnw -q -f auth-center/pom.xml compile exec:java \
 *   -Dexec.mainClass=com.aievolution.authcenter.identity.PasswordHashTool -Dexec.args="$PWD_RAW"
 * </pre>
 */
public final class PasswordHashTool {

  private PasswordHashTool() {}

  public static void main(String[] args) {
    if (args.length != 1 || args[0].isBlank()) {
      System.err.println("用法：PasswordHashTool <明文密码>");
      System.exit(1);
    }
    String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(args[0]);
    System.out.println("{argon2}" + hash);
  }
}

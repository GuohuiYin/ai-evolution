package com.aievolution.authcenter.identity;

import java.time.Instant;

/**
 * 用户账号（W30 #1 identity 域）。role 复用 W29 三角色（ADMIN/WRITER/READER）；status： ACTIVE=可登录，PENDING=注册待审批（#6
 * 默认拒绝语义在表结构层预留）。
 */
public record UserAccount(
    Long id, String username, String passwordHash, Role role, Status status, Instant createdAt) {

  public enum Role {
    ADMIN,
    WRITER,
    READER
  }

  public enum Status {
    ACTIVE,
    PENDING
  }
}

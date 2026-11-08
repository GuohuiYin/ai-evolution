package com.aievolution.authcenter.identity;

import java.util.Optional;

/** 用户账号存储（A10 面向接口：JDBC 为先例实现，未来可换 LDAP/外部 IdP 目录而不换调用方）。 */
public interface UserAccountRepository {

  Optional<UserAccount> findByUsername(String username);

  boolean existsByRole(UserAccount.Role role);

  /** status 由调用方显式给定：种子 admin=ACTIVE，注册新用户=PENDING（#6 默认拒绝）。 */
  void insert(
      String username, String passwordHash, UserAccount.Role role, UserAccount.Status status);

  /** W30 #6 审批面：待激活清单（按注册时间升序，先来的先审）。 */
  java.util.List<UserAccount> findByStatus(UserAccount.Status status);

  /** W30 #6 审批面：状态迁移（PENDING → ACTIVE）。返回是否命中（用户名不存在 = false）。 */
  boolean updateStatus(String username, UserAccount.Status status);
}

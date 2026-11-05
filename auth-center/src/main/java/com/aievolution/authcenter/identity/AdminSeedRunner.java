package com.aievolution.authcenter.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * admin 种子（W30 #1，Owner 裁决：env 过渡 → 首启动幂等落库 MySQL）。fail-closed：用户表无 admin 且未注入种子哈希时拒绝启动——无 admin
 * 的系统无人能审批/管理，是死系统。
 */
@Component
class AdminSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

  private final UserAccountRepository repository;
  private final AuthIdentityProperties properties;

  AdminSeedRunner(UserAccountRepository repository, AuthIdentityProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (repository.existsByRole(UserAccount.Role.ADMIN)) {
      log.info("admin 已存在，种子跳过（幂等）");
      return;
    }
    if (properties.adminPasswordHash() == null || properties.adminPasswordHash().isBlank()) {
      throw new IllegalStateException(
          "用户表无 admin 且未注入 AUTH_ADMIN_PASSWORD_HASH（由 PasswordHashTool 生成）——拒绝启动（fail-closed）");
    }
    repository.insert(
        properties.adminUsername(),
        properties.adminPasswordHash(),
        UserAccount.Role.ADMIN,
        UserAccount.Status.ACTIVE);
    log.info("admin 种子已落库（username={}），env 中的种子哈希可清除", properties.adminUsername());
  }
}

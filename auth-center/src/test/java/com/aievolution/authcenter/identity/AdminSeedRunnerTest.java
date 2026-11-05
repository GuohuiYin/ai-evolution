package com.aievolution.authcenter.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** 种子 Runner fail-closed 单测（不起上下文）：无 admin 且无种子哈希 → 拒绝启动。 */
class AdminSeedRunnerTest {

  @Test
  void failsClosedWhenNoAdminAndNoSeedHash() {
    UserAccountRepository repository = mock(UserAccountRepository.class);
    when(repository.existsByRole(UserAccount.Role.ADMIN)).thenReturn(false);
    AdminSeedRunner runner =
        new AdminSeedRunner(repository, new AuthIdentityProperties("admin", ""));

    assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);
  }
}

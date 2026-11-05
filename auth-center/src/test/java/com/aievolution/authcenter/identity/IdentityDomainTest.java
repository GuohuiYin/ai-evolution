package com.aievolution.authcenter.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/** identity 域行为锁定（W30 #1 步骤 3a）：种子落库 / 幂等 / 种子口令可认证 / pending 默认拒绝。 */
class IdentityDomainTest extends AbstractAuthCenterTest {

  @Autowired private UserAccountRepository repository;
  @Autowired private UserAccountDetailsService userDetailsService;
  @Autowired private AdminSeedRunner seedRunner;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void seedsAdminOnEmptyDatabase() {
    UserAccount admin = repository.findByUsername("admin").orElseThrow();

    assertThat(admin.role()).isEqualTo(UserAccount.Role.ADMIN);
    assertThat(admin.status()).isEqualTo(UserAccount.Status.ACTIVE);
    assertThat(admin.passwordHash()).startsWith("{argon2}");
  }

  @Test
  void seedIsIdempotentOnSecondRun() {
    String hashBefore = repository.findByUsername("admin").orElseThrow().passwordHash();

    seedRunner.run(null);

    assertThat(repository.findByUsername("admin").orElseThrow().passwordHash())
        .isEqualTo(hashBefore);
  }

  @Test
  void seededAdminPasswordAuthenticates() {
    UserDetails admin = userDetailsService.loadUserByUsername("admin");

    assertThat(passwordEncoder.matches(TEST_ADMIN_PASSWORD, admin.getPassword())).isTrue();
    assertThat(admin.isEnabled()).isTrue();
  }

  @Test
  void pendingUserIsDisabled() {
    repository.insert(
        "pending-user", "{noop}x", UserAccount.Role.READER, UserAccount.Status.PENDING);

    UserDetails pending = userDetailsService.loadUserByUsername("pending-user");

    assertThat(pending.isEnabled()).isFalse();
  }
}

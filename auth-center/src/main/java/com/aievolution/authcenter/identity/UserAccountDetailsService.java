package com.aievolution.authcenter.identity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户源（DaoAuthenticationProvider 自动装配消费）。PENDING 用户映射为 disabled——注册待审批期间登录即被拒（#6
 * 默认拒绝语义）。
 */
@Service
class UserAccountDetailsService implements UserDetailsService {

  private final UserAccountRepository repository;

  UserAccountDetailsService(UserAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserAccount account =
        repository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    return User.withUsername(account.username())
        .password(account.passwordHash())
        .roles(account.role().name())
        .disabled(account.status() != UserAccount.Status.ACTIVE)
        .build();
  }
}

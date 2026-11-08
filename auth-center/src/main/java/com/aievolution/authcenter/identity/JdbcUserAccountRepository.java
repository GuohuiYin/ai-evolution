package com.aievolution.authcenter.identity;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@link UserAccountRepository} 的纯 JDBC 实现（对齐主工程 MySQL 姿势：无 JPA/Flyway）。 */
@Repository
class JdbcUserAccountRepository implements UserAccountRepository {

  private final JdbcTemplate jdbc;

  JdbcUserAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UserAccount> findByUsername(String username) {
    var accounts =
        jdbc.query(
            "SELECT id, username, password_hash, role, status, created_at FROM users WHERE username = ?",
            (rs, rowNum) ->
                new UserAccount(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    UserAccount.Role.valueOf(rs.getString("role")),
                    UserAccount.Status.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant()),
            username);
    return accounts.stream().findFirst();
  }

  @Override
  public boolean existsByRole(UserAccount.Role role) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE role = ?", Integer.class, role.name());
    return count != null && count > 0;
  }

  @Override
  public void insert(
      String username, String passwordHash, UserAccount.Role role, UserAccount.Status status) {
    jdbc.update(
        "INSERT INTO users (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
        username,
        passwordHash,
        role.name(),
        status.name());
  }

  @Override
  public java.util.List<UserAccount> findByStatus(UserAccount.Status status) {
    return jdbc.query(
        "SELECT id, username, password_hash, role, status, created_at FROM users WHERE status = ? ORDER BY created_at",
        (rs, rowNum) ->
            new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                UserAccount.Role.valueOf(rs.getString("role")),
                UserAccount.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant()),
        status.name());
  }

  @Override
  public boolean updateStatus(String username, UserAccount.Status status) {
    return jdbc.update("UPDATE users SET status = ? WHERE username = ?", status.name(), username)
        > 0;
  }
}

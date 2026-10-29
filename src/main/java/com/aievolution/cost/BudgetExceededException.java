package com.aievolution.cost;

/**
 * 日 token 预算熔断异常（W25 #2b，fail-closed）：已用量达预算上限，或账本不可读 无法证明未超支时，由 {@code BudgetGuardAdvisor}
 * 抛出；{@code ChatExceptionHandler} 映射为 503 ProblemDetail。
 */
public class BudgetExceededException extends RuntimeException {

  public BudgetExceededException(String message) {
    super(message);
  }

  public BudgetExceededException(String message, Throwable cause) {
    super(message, cause);
  }
}

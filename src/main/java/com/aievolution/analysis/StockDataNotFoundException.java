package com.aievolution.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 数据源未覆盖该股票代码——空数据不交给模型（与 RAG 的空检索硬拒答同一哲学）。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class StockDataNotFoundException extends RuntimeException {

  public StockDataNotFoundException(String code) {
    super("数据源未覆盖该股票代码: " + code);
  }
}

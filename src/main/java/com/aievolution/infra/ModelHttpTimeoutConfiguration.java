package com.aievolution.infra;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import reactor.netty.http.client.HttpClient;

/**
 * 模型 HTTP 调用超时兜底（W27 #3b）：DeepSeek 断流的另一种形态=连接半开**永久挂起**—— 响应体读取 park
 * 在无超时的流上，failover/重试/单案隔离这些异常驱动机制全部失效（不抛异常的故障无人能接）。 给共享 {@code RestClient.Builder} 装上
 * connect/response 超时：挂起变异常，异常驱动机制才接得住。
 *
 * <p>取值口径：read 默认 PT3M（与 eval 值守口径同源；pipeline plan/review 活动预算 120s 更紧， 在 Temporal 层先行触发），connect
 * 10s。均外置配置（A11 策略值不过夜）。
 *
 * <p>W31 #1：{@link ArmTimeoutProperties} 按臂超时预算表在本类注册——本类是超时策略的既有落点， 且无条件装配（按臂查表的消费方不随 failover
 * 开关或引擎旗标缺席）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArmTimeoutProperties.class)
public class ModelHttpTimeoutConfiguration {

  @Bean
  RestClientCustomizer modelHttpTimeouts(
      @Value("${ai.http.model.connect-timeout:PT10S}") Duration connectTimeout,
      @Value("${ai.http.model.read-timeout:PT3M}") Duration readTimeout) {
    HttpClient httpClient =
        HttpClient.create()
            .responseTimeout(readTimeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());
    ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory(httpClient);
    return builder -> builder.requestFactory(factory);
  }
}

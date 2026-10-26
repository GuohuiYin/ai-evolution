package com.aievolution.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 主体配额装配（W23 #2）：配置绑定 + Guard Bean。 */
@Configuration
@EnableConfigurationProperties(RetrievalDiversityProperties.class)
public class RetrievalDiversityConfig {

  @Bean
  public RetrievalDiversityGuard retrievalDiversityGuard(RetrievalDiversityProperties props) {
    return new RetrievalDiversityGuard(props.enabled(), props.subjects());
  }
}

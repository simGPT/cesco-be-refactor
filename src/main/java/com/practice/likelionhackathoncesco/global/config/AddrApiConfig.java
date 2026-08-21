package com.practice.likelionhackathoncesco.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
public class AddrApiConfig {

  @Value("${addr.key}")
  private String confmKey;

  @Value("${addr.url}")
  private String confmUrl;
}

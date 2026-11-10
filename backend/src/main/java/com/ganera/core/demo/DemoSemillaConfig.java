package com.ganera.core.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoSemillaProperties.class)
public class DemoSemillaConfig {
}

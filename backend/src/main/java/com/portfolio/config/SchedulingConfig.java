package com.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs (rate-limit cleanup, visit purge).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig
{
}

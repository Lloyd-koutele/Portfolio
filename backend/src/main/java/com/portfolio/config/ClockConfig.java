package com.portfolio.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UTC clock, injectable so that time-dependent code can be tested.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig
{
    @Bean
    Clock clock()
    {
        return Clock.systemUTC();
    }
}

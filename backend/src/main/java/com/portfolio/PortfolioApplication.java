package com.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Entry point. Each direct sub-package of {@code com.portfolio} is a Spring Modulith application module.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(systemName = "Portfolio", sharedModules = "common")
public class PortfolioApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}

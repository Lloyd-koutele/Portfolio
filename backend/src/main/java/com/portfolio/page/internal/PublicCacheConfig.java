package com.portfolio.page.internal;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag on the public book: readers' browsers revalidate it (Cache-Control: no-cache) and get a 304 without body
 * until a new edition is published.
 */
@Configuration(proxyBeanMethods = false)
class PublicCacheConfig
{
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> publicBookEtag()
    {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
            new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/public/book", "/api/public/pages/*");
        registration.setName("publicBookEtag");
        return registration;
    }
}

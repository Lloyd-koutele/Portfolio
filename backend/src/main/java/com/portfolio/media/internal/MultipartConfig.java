package com.portfolio.media.internal;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits follow the video limit; uploads are written to temporary files, never kept whole in memory.
 * Images get their own 5 MB check in {@link MediaService}.
 */
@Configuration(proxyBeanMethods = false)
class MultipartConfig
{
    @Bean
    MultipartConfigElement multipartConfigElement(MediaProperties properties)
    {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(properties.maxVideoBytes()));
        factory.setMaxRequestSize(DataSize.ofBytes(properties.maxVideoBytes() + MediaProperties.MEGABYTE));
        factory.setFileSizeThreshold(DataSize.ofKilobytes(512));
        return factory.createMultipartConfig();
    }
}

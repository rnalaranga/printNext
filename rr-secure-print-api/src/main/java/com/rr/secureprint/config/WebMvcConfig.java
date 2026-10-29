package com.rr.secureprint.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Javascript assets
        registry.addResourceHandler("/js/**")
                .addResourceLocations("file:./rr-secure-print-web/js/", "file:../rr-secure-print-web/js/", "classpath:/static/js/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());

        // CSS assets
        registry.addResourceHandler("/css/**")
                .addResourceLocations("file:./rr-secure-print-web/css/", "file:../rr-secure-print-web/css/", "classpath:/static/css/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());

        // Default static resources (HTML, icons, terminal)
        registry.addResourceHandler("/**")
                .addResourceLocations("file:./rr-secure-print-web/", "file:../rr-secure-print-web/", "classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
    }
}

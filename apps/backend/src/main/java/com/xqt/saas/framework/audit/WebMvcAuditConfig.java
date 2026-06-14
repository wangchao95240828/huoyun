package com.xqt.saas.framework.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册写操作审计 Interceptor。
 */
@Configuration
public class WebMvcAuditConfig implements WebMvcConfigurer {
    private final WriteAuditInterceptor writeAuditInterceptor;

    public WebMvcAuditConfig(WriteAuditInterceptor writeAuditInterceptor) {
        this.writeAuditInterceptor = writeAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(writeAuditInterceptor)
                .addPathPatterns("/api/**");
    }
}

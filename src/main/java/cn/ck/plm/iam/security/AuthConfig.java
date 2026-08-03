/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置 —— 注册认证拦截器并设置白名单、静态资源映射。
 */
@Configuration
public class AuthConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    private final AuthInterceptor authInterceptor;

    @Value("${plm.storage.base-path}")
    private String rawBasePath;

    private Path basePath;

    public AuthConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @PostConstruct
    void initBasePath() {
        this.basePath = Paths.get(rawBasePath).toAbsolutePath().normalize();
        log.info("文件存储根目录: {}", basePath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/verify",
                        "/api/tenants/register",
                        "/api/notifications/**",
                        "/api/ckfiles/**",
                        "/api/attachments/**",
                        "/error"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 文件路径格式：/{tenantOid}/{category}/{uuid}.ext
        // 直接映射 basePath 作为静态资源根目录
        String location = basePath.toUri().toString();
        if (!location.endsWith("/")) location += "/";
        log.info("文件资源映射: /** -> {}", location);
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCachePeriod(0);
    }
}

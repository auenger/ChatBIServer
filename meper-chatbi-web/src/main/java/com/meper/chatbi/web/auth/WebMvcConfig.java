package com.meper.chatbi.web.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * API 认证拦截注册：/api/** 全量拦截，仅登录接口豁免；actuator 不在 /api 下天然豁免。
 *
 * <p>CORS：允许的前端 Origin 由 {@code meper.web.cors.allowed-origins} 配置（逗号分隔）。
 * 默认仅放开本地开发前端（8001）；生产跨源部署必须显式配置精确 Origin，同源部署无需额外放开。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebMvcConfig(
            AuthInterceptor authInterceptor,
            @Value("${meper.web.cors.allowed-origins:http://localhost:8001}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(origin -> {
                    if (origin.contains("*")) {
                        throw new IllegalArgumentException("CORS allowed origin 必须是精确 Origin，不能包含通配符");
                    }
                })
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/ping");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

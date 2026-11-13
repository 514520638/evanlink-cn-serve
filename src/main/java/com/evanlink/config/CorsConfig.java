package com.evanlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(org.springframework.core.env.Environment environment) {
        CorsConfiguration config = new CorsConfiguration();
        
        // Cookie 认证接口不能向任意站点开放带凭据跨域访问。
        for (String origin : environment.getProperty("APP_ALLOWED_ORIGINS", "https://evanlink.cn,https://www.evanlink.cn,http://127.0.0.1:8081,http://localhost:8081").split(",")) {
            if (origin.trim().equals("*")) throw new IllegalArgumentException("APP_ALLOWED_ORIGINS cannot contain *");
            if (!origin.isBlank()) config.addAllowedOrigin(origin.trim());
        }
        
        // 允许携带认证信息
        config.setAllowCredentials(true);
        
        // 允许所有请求头
        config.addAllowedHeader("*");
        
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        
        // 预检请求缓存时间
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

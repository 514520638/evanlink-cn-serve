package com.evanlink.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.ArrayList;
import java.util.List;

/** 数据库初始化前检查配置，缺少凭据时给出可操作提示，不输出任何配置值。 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfigurationCheck {
    public static List<String> missing(Environment environment) {
        var result = new ArrayList<String>();
        if (environment.getProperty("spring.datasource.username", "").isBlank()) result.add("SPRING_DATASOURCE_USERNAME");
        // 本地数据库允许显式空密码，但不能把遗漏配置当成空密码尝试连接。
        if (environment.getProperty("SPRING_DATASOURCE_PASSWORD") == null
                && environment.getProperty("spring.datasource.password") == null) result.add("SPRING_DATASOURCE_PASSWORD");
        return result;
    }

    @Bean
    public static BeanFactoryPostProcessor checkDatabaseConfiguration(Environment environment) {
        return factory -> {
            var missing = missing(environment);
            if (!missing.isEmpty()) throw new IllegalStateException(
                "数据库配置缺失：" + String.join(", ", missing)
                + "。请在后端工作目录的 .env 或容器环境中配置 SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD，再重启 Java。真实凭据不要写入源码。");
        };
    }
}

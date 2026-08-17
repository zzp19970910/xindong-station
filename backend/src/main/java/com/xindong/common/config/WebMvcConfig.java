package com.xindong.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Environment env;

    public WebMvcConfig(Environment env) {
        this.env = env;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        boolean embed = Arrays.asList(env.getActiveProfiles()).contains("embed-frontend")
                || "embed-frontend".equals(env.getProperty("spring.profiles.active"));
        if (!embed) {
            // 前后端分离模式：后端仅提供 /api/v1/** 接口，不承载前端页面
            // 访问 8080/ 直接返回 404 或 Spring 默认欢迎页，不会 forward 到不存在的 index.html
            return;
        }
        String[] routes = new String[]{
                "/", "/dashboard", "/home",
                "/mood", "/diary", "/anniversary", "/record",
                "/interactive",
                "/interactive/icebreak", "/interactive/wish", "/interactive/wish-new",
                "/interactive/quiz-daily", "/interactive/tacit-game",
                "/interactive/pm", "/interactive/messages",
                "/settings", "/settings/coin-center", "/settings/profile",
                "/login", "/register", "/bind", "/404", "/500"
        };
        for (String r : routes) {
            registry.addViewController(r).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**", "/favicon.ico", "/svg/**", "/img/**", "/icons/**")
                .addResourceLocations("classpath:/static/assets/", "classpath:/static/", "classpath:/public/")
                .setCachePeriod(31536000);
    }
}
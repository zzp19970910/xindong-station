package com.xindong.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

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
        // SPA History模式通用写法：任何不包含"."的路径（排除静态资源js/css/png等）全部forward到/index.html
        // 优先级低于@RequestMapping精确匹配，不会影响/api/v1/**真实接口
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/{x:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{x}/{y:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{x}/{y}/{z:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{x}/{y}/{z}/{w:[^\\.]*}").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/", "classpath:/META-INF/resources/")
                .setCachePeriod(31536000);
    }
}
package com.xindong.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
    public void addInterceptors(InterceptorRegistry registry) {
        // Vue Router History模式全局拦截器：GET请求+URI不含"."+Accept含text/html → forward到/index.html
        // Spring 6 官方HandlerInterceptor接口，不用@Deprecated的HandlerInterceptorAdapter
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) throws Exception {
                if (!RequestMethod.GET.name().equals(request.getMethod())) {
                    return true;
                }
                String uri = request.getRequestURI();
                // 1. 明确排除：带后缀的静态资源 / API前缀 / Actuator / Swagger / 已登录相关接口
                if (uri.contains(".") || uri.startsWith("/api/") || uri.startsWith("/auth/") ||
                        uri.startsWith("/actuator/") || uri.startsWith("/swagger") ||
                        uri.startsWith("/v3/api-docs") || uri.startsWith("/api-docs") ||
                        uri.startsWith("/couple/") || uri.equals("/error")) {
                    return true;
                }
                String accept = request.getHeader("Accept");
                if (accept == null || accept.isBlank()) return true;
                boolean isHtmlRequest = accept.contains("text/html") || accept.contains("application/xhtml+xml") || accept.contains("*/*");
                if (!isHtmlRequest) {
                    return true;
                }
                // / 和 /index.html 交给Spring Boot欢迎页机制（classpath:/static/index.html）
                if ("/".equals(uri) || "/index.html".equals(uri)) {
                    return true;
                }
                // 其他纯路径：/login, /settings/coin-center 等 → Vue Router History forward
                request.getRequestDispatcher("/index.html").forward(request, response);
                return false;
            }
        }).addPathPatterns("/**").order(Integer.MIN_VALUE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/", "classpath:/META-INF/resources/")
                .setCachePeriod(31536000);
    }
}
package com.xindong.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    /**
     * 匹配：GET请求 + Accept头包含text/html（浏览器直接输入网址/F5刷新Vue History路径场景）
     * Spring Security 6 官方推荐：不用非法{var:regex}，按HTTP语义+Header精准匹配
     */
    private static final RequestMatcher SPA_HISTORY_GET_MATCHER = new AndRequestMatcher(
            (RequestMatcher) request -> HttpMethod.GET.matches(request.getMethod()),
            (RequestMatcher) request -> {
                String accept = request.getHeader(HttpHeaders.ACCEPT);
                if (accept == null || accept.isBlank()) return false;
                // text/html或*/*（浏览器默认Accept通常是 "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"）
                return accept.contains(MediaType.TEXT_HTML_VALUE) || accept.contains("application/xhtml+xml") || accept.contains("*/*");
            },
            // 额外保险：URI不含"."（排除明确的静态资源后缀，避免forward把js/css也当html）
            (RequestMatcher) request -> !request.getRequestURI().contains(".")
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            writeJsonSec(response, objectMapper, ErrorCode.AUTH_REQUIRED);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            writeJsonSec(response, objectMapper, ErrorCode.COUPLE_DATA_FORBIDDEN);
                        })
                )
                .authorizeHttpRequests(a -> a
                        // === 1. 静态资源（明确后缀）：100%放行，Spring Security 6合法Ant Path ===
                        // （这些不走DispatcherServlet，Tomcat DefaultServlet直接处理，permitAll这里纯兜底）
                        .requestMatchers(HttpMethod.GET,
                                "/*.js", "/*.css", "/*.map",
                                "/*.svg", "/*.png", "/*.jpg", "/*.jpeg",
                                "/*.gif", "/*.webp", "/*.ico",
                                "/*.woff", "/*.woff2", "/*.ttf", "/*.eot",
                                "/favicon.ico", "/robots.txt"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/assets/**", "/node_modules/**", "/fonts/**", "/img/**", "/images/**"
                        ).permitAll()
                        // === 2. Actuator / Swagger 白名单（不走DispatcherServlet，兜底）===
                        .requestMatchers(
                                "/", "/index.html",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics/**",
                                "/error"
                        ).permitAll()
                        // === 3. Spring MVC真实Controller接口白名单（无前缀！和前端打包的axios请求路径100%对齐）===
                        // 前端打包auth.api-BXGJ8_R2.js明确写的: o.post("/auth/login")、o.post("/couple/bind") 没有/api/v1!
                        .requestMatchers(
                                "/auth/**",
                                "/couple/verify-invite"
                        ).permitAll()
                        // === 4. Vue Router History模式：GET + Accept头text/html + URI不含. → 放行（浏览器F5刷新不401）===
                        .requestMatchers(SPA_HISTORY_GET_MATCHER).permitAll()
                        // === 5. 公共GET数据接口（无前缀！和前端打包请求路径对齐）===
                        .requestMatchers(HttpMethod.GET,
                                "/quiz/**",
                                "/checklists",
                                "/weekly/**",
                                "/anniversaries/**",
                                "/mood/types",
                                "/daily-quiz/today"
                        ).permitAll()
                        // === 6. 其他所有请求：必须JWT认证 ===
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void writeJsonSec(HttpServletResponse response, ObjectMapper om, ErrorCode code) throws java.io.IOException {
        String rawCode = code.getCode();
        int httpStatus = switch (rawCode) {
            case "20701", "20801", "20301" -> 409;
            case "30004" -> 404;
            case "4003" -> 403;
            case "50703" -> 500;
            default -> 200;
        };
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter w = response.getWriter()) {
            w.write(om.writeValueAsString(Result.error(code)));
        }
    }
}
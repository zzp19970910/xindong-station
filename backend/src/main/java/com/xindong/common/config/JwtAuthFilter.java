package com.xindong.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.result.Result;
import com.xindong.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> USER_ROLES =
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

    private static final java.util.regex.Pattern STATIC_URI =
            java.util.regex.Pattern.compile(".*\\.(js|css|map|svg|png|jpg|jpeg|gif|webp|ico|woff2?|ttf|eot|html?|txt)$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 1. 所有静态资源文件（带后缀）直接跳过过滤器，不碰JWT，绝不可能误写30006
        if (STATIC_URI.matcher(uri).matches() || uri.startsWith("/assets/") || uri.startsWith("/node_modules/")) {
            return true;
        }
        // 2. 明确的白名单路径直接跳过（和SecurityConfig.permitAll保持一致）
        if (uri.equals("/") || uri.equals("/index.html") || uri.equals("/favicon.ico") || uri.equals("/robots.txt") ||
                uri.startsWith("/auth/") || uri.startsWith("/swagger-ui") || uri.equals("/swagger-ui.html") ||
                uri.startsWith("/api-docs") || uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/actuator/health") || uri.startsWith("/actuator/info") ||
                uri.equals("/error")) {
            return true;
        }
        // 3. Vue Router History路径：GET请求 + Accept头包含text/html（浏览器F5刷新/直接输入网址）
        String accept = request.getHeader(org.springframework.http.HttpHeaders.ACCEPT);
        if (org.springframework.http.HttpMethod.GET.matches(request.getMethod()) && accept != null
                && (accept.contains("text/html") || accept.contains("application/xhtml+xml"))) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        String adminToken = request.getHeader("X-Admin-Token");
        String token = null;
        try {
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            } else if (adminToken != null && !adminToken.isBlank()) {
                token = adminToken;
            }
            if (token != null) {
                CoupleContext ctx = jwtUtil.parseToken(token);
                CoupleContext.set(ctx);
                UsernamePasswordAuthenticationToken authObj =
                        new UsernamePasswordAuthenticationToken(ctx, null, USER_ROLES);
                SecurityContextHolder.getContext().setAuthentication(authObj);
                log.trace("[JWT] uid={}, cid={}, pIdx={}, uri={}",
                        ctx.getUserId(), ctx.getCoupleId(), ctx.getPartnerIdx(), request.getRequestURI());
            }
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            // 只有明确的BusinessException(TOKEN过期/签名错)才返30006系列
            SecurityContextHolder.clearContext();
            writeJsonError(response, e.getCodeValue(), e.getMsg());
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            // Spring Security鉴权失败抛这个，往后面抛给exceptionHandling处理(返403)
            throw ade;
        } catch (jakarta.servlet.ServletException | IOException se) {
            // Servlet/IO原异常直接抛出
            throw se;
        } catch (RuntimeException re) {
            // 其他RuntimeException(PatternSyntaxException/IllegalArgument等)绝对不是JWT错！
            // -> 清上下文后继续走过滤链，给后面的Servlet/Filter处理，绝不给前端写30006 JSON！
            log.warn("[JWT Filter 非JWT异常放过] uri={} err={}", request.getRequestURI(), re.toString());
            SecurityContextHolder.clearContext();
            CoupleContext.clear();
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 最后兜底：打ERROR日志后放过，绝不写JSON
            log.error("[JWT Filter 异常放过] uri={}", request.getRequestURI(), e);
            SecurityContextHolder.clearContext();
            CoupleContext.clear();
            chain.doFilter(request, response);
        } finally {
            CoupleContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeJsonError(HttpServletResponse response, String code, String msg) throws IOException {
        int httpStatus = switch (code) {
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
            w.write(objectMapper.writeValueAsString(Result.error(code, msg)));
        }
    }
}
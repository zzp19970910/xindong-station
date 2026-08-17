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

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

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
            SecurityContextHolder.clearContext();
            writeJsonError(response, e.getCodeValue(), e.getMsg());
        } catch (Exception e) {
            log.warn("[JWT解析失败] uri={}, token前缀={} err={}",
                    request.getRequestURI(),
                    token == null ? "NULL" : token.substring(0, Math.min(token.length(), 20)),
                    e.getMessage());
            SecurityContextHolder.clearContext();
            writeJsonError(response, ErrorCode.TOKEN_INVALID.getCode(), ErrorCode.TOKEN_INVALID.getMsg());
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
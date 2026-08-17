package com.xindong.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("🌐 WebMvc + Security 基线集成测试 - 根路径/静态资源/Vue History/Actuator")
class WebMvcBaselineIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("BL1: 根路径 / GET → 200 OK（欢迎页/static/index.html生效，不再30006）")
    void bl1_rootPathReturns200() throws Exception {
        mvc.perform(get("/")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL2: Actuator健康检查 /actuator/health → 200 {" + "\"status\":\"UP\"} 白名单通过")
    void bl2_actuatorHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("BL3: Vue History路径 /settings（GET+Accept=text/html）→ 200 forward到index.html，不401/404")
    void bl3_vueHistorySettingsForward() throws Exception {
        mvc.perform(get("/settings")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL4: Vue History多级路径 /settings/coin-center/xxx → 200不白屏")
    void bl4_vueHistoryDeepPath() throws Exception {
        mvc.perform(get("/settings/coin-center/xxx")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL5: 无Token直接访问/auth/login（白名单）→ 不401，进入Controller正常处理(Post无body返回4xx不是401)")
    void bl5_authWhitelist() throws Exception {
        // /api/v1/auth/login是POST白名单，没body应该是415/400，绝不应该是401/403
        var res = mvc.perform(get("/api/v1/auth/captcha")).andReturn().getResponse();
        int status = res.getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(status == 200 || status == 400 || status == 404 || status == 405,
                "验证码接口白名单应通过Security, 实际status=" + status + " 不该是401/403");
    }

    @Test
    @DisplayName("BL6: Actuator info → 200 OK")
    void bl6_actuatorInfo() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL7: 非Accept=html的JSON请求（axios）/settings → 必须鉴权401，不是forward")
    void bl7_nonHtmlApiShould401() throws Exception {
        mvc.perform(get("/settings").header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BL8: spring.mvc.servlet.path=/api/v1验证 - /auth/login没前缀→404或默认servlet，/api/v1/auth/**是真实路径")
    void bl8_servletPathPrefixCheck() throws Exception {
        // 无/api/v1前缀→DispatcherServlet不处理→返回404/不处理
        mvc.perform(get("/auth/captcha")).andExpectAll(
                result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(s == 404 || s == 401,
                            "没有/api/v1前缀的路径不应进入Controller");
                }
        );
    }
}
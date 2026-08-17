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
@DisplayName("WebMvc + Security 基线集成测试 - 根路径/静态资源/Vue History/Actuator")
class WebMvcBaselineIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("BL1: 根路径 / GET -> 200 OK（欢迎页/static/index.html生效，不再30006）")
    void bl1_rootPathReturns200() throws Exception {
        mvc.perform(get("/")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL2: Actuator健康检查 /actuator/health -> 200 status=UP 白名单通过")
    void bl2_actuatorHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("BL3: Vue History路径 /settings（GET+Accept=text/html）-> 200 forward到index.html，不401/404")
    void bl3_vueHistorySettingsForward() throws Exception {
        mvc.perform(get("/settings")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL4: Vue History多级路径 /settings/coin-center/xxx -> 200不白屏")
    void bl4_vueHistoryDeepPath() throws Exception {
        mvc.perform(get("/settings/coin-center/xxx")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL5: 无Token直接访问/auth/captcha（白名单）-> 不401，进入Controller正常处理(4xx/2xx都可，只要不是401/403)")
    void bl5_authWhitelist() throws Exception {
        var res = mvc.perform(get("/auth/captcha")).andReturn().getResponse();
        int status = res.getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(status != 401 && status != 403,
                "验证码接口白名单应通过Security, 实际status=" + status + " 不该是401/403");
    }

    @Test
    @DisplayName("BL6: Actuator info -> 200 OK")
    void bl6_actuatorInfo() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BL7: 非Accept=html的JSON请求（axios）/settings -> 必须鉴权401，不是forward")
    void bl7_nonHtmlApiShould401() throws Exception {
        mvc.perform(get("/settings").header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BL8: servlet.path=空基线验证: /auth/captcha直连通过Security，/api/v1/xxx旧前缀绝对404(禁用!)")
    void bl8_servletPathPrefixCheck() throws Exception {
        // (1) 正确无前缀 /auth/captcha -> Security白名单放行，进Controller(只要不是401/403都行)
        int s1 = mvc.perform(get("/auth/captcha")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s1 != 401 && s1 != 403,
                "无前缀/auth/是白名单，必须通过Security! 实际status=" + s1);
        // (2) 错误旧前缀 /api/v1/auth/captcha -> 未配置servlet.path，DispatcherServlet不认→404
        mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isNotFound());
    }
}

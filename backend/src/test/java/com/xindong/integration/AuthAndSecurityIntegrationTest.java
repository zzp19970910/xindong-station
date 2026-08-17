package com.xindong.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("🔐 认证鉴权全场景集成测试 - 登录/JWT/Security白名单")
class AuthAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void issueToken() {
        com.xindong.common.context.CoupleContext ctx = new com.xindong.common.context.CoupleContext();
        ctx.setUserId(1001L);
        ctx.setPhone("13800000001");
        ctx.setNickname("测试小明");
        ctx.setCoupleId(500L);
        ctx.setPartnerIdx(1);
        validToken = jwtUtil.generateToken(ctx);
    }

    @Test
    @DisplayName("AU1: 无Token访问需要鉴权接口 → HTTP 401 + code=AUTH_REQUIRED=30005")
    void au1_noToken401() throws Exception {
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.AUTH_REQUIRED.getCode()), body.get("code").asText());
    }

    @Test
    @DisplayName("AU2: 无效Token Bearer garbage → TOKEN_INVALID=30006")
    void au2_invalidToken30006() throws Exception {
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()), body.get("code").asText(),
                "无效Token返code=30006, 实际=" + body.get("code"));
    }

    @Test
    @DisplayName("AU3: 合法Bearer Token访问鉴权接口 → 200 OK进入Controller(不401)")
    void au3_validTokenPass() throws Exception {
        // home/me返回可能业务错(用户不在DB)，但Security/JWT不会拦，Status必须是200 OK
        // (BusinessException会通过正常响应HTTP 200+业务code返回，不是401)
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(body.has("code"), "必须是统一Result格式, 实际=" + body);
        assertNotEquals(String.valueOf(ErrorCode.AUTH_REQUIRED.getCode()), body.get("code").asText(),
                "合法Token绝不是AUTH_REQUIRED");
        assertNotEquals(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()), body.get("code").asText(),
                "合法Token绝不是TOKEN_INVALID");
    }

    @Test
    @DisplayName("AU4: 红线TEST-A-108作为X-Admin-Token头传 → 解析成功不401")
    void au4_redlineTestTokenAdminHeader() throws Exception {
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .header("X-Admin-Token", "TEST-A-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()), body.get("code").asText(),
                "TEST-A-108红线专用Token应解析成功");
    }

    @Test
    @DisplayName("AU5: 红线TEST-B-108作为Bearer传 → 解析成功")
    void au5_redlineTestTokenBearer() throws Exception {
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-B-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()), body.get("code").asText(),
                "TEST-B-108 Bearer方式也应解析成功");
    }

    @Test
    @DisplayName("AU6: 公共白名单GET接口/quiz/** 无Token也→200不401")
    void au6_quizPublicWhitelist() throws Exception {
        MvcResult res = mvc.perform(get("/quiz/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.AUTH_REQUIRED.getCode()), body.get("code").asText(),
                "quiz属于公共白名单，不该要鉴权");
    }

    @Test
    @DisplayName("AU7: 登录接口/auth/login POST 缺body → 400/415但绝不401/403（白名单通过Security）")
    void au7_loginWhitelistNo401() throws Exception {
        int status = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 400 || status == 415 || status == 422,
                "登录接口属于白名单! Status应是4xx(参数错), 实际=" + status + " 绝不能是401/403！");
    }

    @Test
    @DisplayName("AU8: JWT前缀不是Bearer + Token格式正常 → 不会解析当作没Token→401")
    void au8_wrongAuthPrefix() throws Exception {
        mvc.perform(get("/interactive/home/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + validToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AU9: 空字符串Bearer → TOKEN_INVALID=30006")
    void au9_emptyBearerInvalid() throws Exception {
        MvcResult res = mvc.perform(get("/interactive/home/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.TOKEN_INVALID.getCode()), body.get("code").asText());
    }
}
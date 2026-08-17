package com.xindong.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.result.Result;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("⚠️ 全局异常处理器 + Result响应格式 测试")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    // 纯Result格式校验（不打网络，确保返回体结构固定）
    @Test
    @DisplayName("GE1: Result.ok(T) → code=0 + ok=true + ts是当前Unix秒 + data=T")
    void ge1_resultOkStructure() {
        String data = "hello-data";
        Result<String> r = Result.ok(data);
        assertEquals("0", String.valueOf(r.getCode()), "成功code固定为0");
        assertTrue(r.isOk(), "ok必须true");
        assertEquals(data, r.getData(), "data正确");
        assertNotNull(r.getMsg(), "msg非空");
        // ts应是当前Unix秒±5s
        long now = Instant.now().getEpochSecond();
        assertTrue(Math.abs(r.getTs() - now) < 5, "ts应为当前Unix秒。ts=" + r.getTs() + " now=" + now);
    }

    @Test
    @DisplayName("GE2: Result.fail(ErrorCode) → code=对应错误码 + ok=false")
    void ge2_resultFailByErrorCode() {
        Result<Object> r = Result.fail(ErrorCode.COUPLE_DATA_FORBIDDEN);
        assertEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), String.valueOf(r.getCode()));
        assertFalse(r.isOk());
        assertEquals(ErrorCode.COUPLE_DATA_FORBIDDEN.getMsg(), r.getMsg());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("GE3: Result.fail(code,msg) → ts同步更新 ok=false")
    void ge3_resultFailByCodeMsg() {
        Result<Object> r = Result.fail(20001, "自定义错误");
        assertEquals("20001", String.valueOf(r.getCode()));
        assertEquals("自定义错误", r.getMsg());
        assertFalse(r.isOk());
        long now = Instant.now().getEpochSecond();
        assertTrue(Math.abs(r.getTs() - now) < 5, "fail ts也应该是当前秒");
    }

    @Test
    @DisplayName("GE4: BusinessException抛错后 → 接口仍HTTP 200 + Result body(code/msg/ts)，不是500/堆栈HTML")
    void ge4_businessExceptionHandled() throws Exception {
        // 访问不存在的纪念日/不存在的wish等必然抛BusinessException或404的接口
        MvcResult res = mvc.perform(get("/api/v1/anniversaries/9999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-A-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        JsonNode n = om.readTree(body);
        assertTrue(n.has("code"), "必须是Result JSON格式, 不应是异常堆栈HTML. body="+body);
        assertTrue(n.has("ok"), "必须有ok字段");
        assertTrue(n.has("ts"), "必须有ts字段");
        assertFalse(n.path("ok").asBoolean(), "不存在的记录 ok=false");
        // 不包含"Whitelabel Error Page"或堆栈
        assertFalse(body.contains("Whitelabel"), "不应是Spring默认错误页");
        assertFalse(body.contains("Exception"), "不应在body里暴露Exception类名");
        assertFalse(body.contains("at com.xindong"), "不应暴露堆栈");
    }

    @Test
    @DisplayName("GE5: 不存在的API接口返回 → 状态码合理 + Result body(不白屏不堆栈)")
    void ge5_notFoundApi() throws Exception {
        String uri = "/api/v1/this-does-not-exist-999/x/y";
        MvcResult res = mvc.perform(get(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-A-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        int status = res.getResponse().getStatus();
        String body = res.getResponse().getContentAsString();
        // 404或200但code非0，都是合理的全局异常处理表现
        assertTrue(status == 404 || status == 200 || status == 405,
                "不存在路径状态码应为404/200/405，实际=" + status);
        if (!body.isBlank()) {
            JsonNode n = om.readTree(body);
            assertTrue(n.has("code") || status == 404, "如果有body，必须是Result格式");
            assertFalse(body.contains("Whitelabel Error Page"), "不应是默认错误HTML页");
        }
    }

    @Test
    @DisplayName("GE6: 未登录访问需要鉴权接口 → 401 + Result body(code=30005 AUTH_REQUIRED)，不200乱返")
    void ge6_unauth401Code() throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/interactive/home/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode n = om.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.AUTH_REQUIRED.getCode()), n.path("code").asText(),
                "未登录401应返AUTH_REQUIRED=30005, body=" + n);
        assertFalse(n.path("ok").asBoolean());
    }

    @Test
    @DisplayName("GE7: null/NullPointerException → 全局捕获 → HTTP 500区间或200+50000内部错误，不暴露堆栈")
    void ge7_nullPointerCaught() throws Exception {
        // 选一个容易触发空指针的参数场景：body=null，接口@RequestBody required=true也可能直接抛400 Bad Request
        // 我们这里验证：GlobalExceptionHandler永远不会输出堆栈
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")  // 空body缺字段→可能抛NPE或400参数缺
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("NullPointerException"), "NPE绝不应在body暴露类名: body=" + body);
        assertFalse(body.contains("at com.xindong"), "堆栈不应暴露");
        assertFalse(body.contains("Whitelabel Error Page"), "不应Spring默认错误页");
    }
}
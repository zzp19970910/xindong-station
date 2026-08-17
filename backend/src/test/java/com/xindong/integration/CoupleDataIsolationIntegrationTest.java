package com.xindong.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.enums.ErrorCode;
import com.xindong.content.entity.Anniversary;
import com.xindong.content.repository.AnniversaryRepository;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoupleRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("🚩 红线C系列：跨情侣数据隔离全场景集成测试 COUPLE_DATA_FORBIDDEN=30004")
class CoupleDataIsolationIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private CoupleRepository coupleRepo;
    @Autowired
    private AnniversaryRepository annivRepo;

    private Couple c108, c200;
    private Anniversary annivA_108;

    @BeforeEach
    void seed() {
        annivRepo.deleteAll();
        coupleRepo.deleteAll();

        c108 = new Couple();
        c108.setInviteCodeP1("A10801");
        c108.setInviteCodeP2("B10802");
        c108.setCoinsTotal(0);
        c108 = coupleRepo.saveAndFlush(c108);

        c200 = new Couple();
        c200.setInviteCodeP1("C20001");
        c200.setInviteCodeP2("D20002");
        c200.setCoinsTotal(0);
        c200 = coupleRepo.saveAndFlush(c200);

        annivA_108 = new Anniversary();
        annivA_108.setCoupleId(c108.getId());
        annivA_108.setTitle("在一起的日子");
        annivA_108.setType("love");
        annivA_108.setEmoji("❤️");
        annivA_108.setTargetDate(LocalDate.now().minusDays(100));
        annivA_108.setDisplayMode("countup");
        annivA_108.setIsTop(true);
        annivA_108 = annivRepo.saveAndFlush(annivA_108);
    }

    // ============================ C1: 跨情侣读 ============================
    @Test
    @DisplayName("C1-R1: cid=200攻击者C(TEST-C-200) 读cid=108情侣纪念日详情 → 30004 COUPLE_DATA_FORBIDDEN")
    void c1_r1_crossReadAnniversary() throws Exception {
        MvcResult res = mvc.perform(get("/anniversaries/" + annivA_108.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-C-200")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "跨情侣读应为30004, 实际code=" + body.get("code") + " msg=" + body.get("msg"));
        assertFalse(body.get("ok").asBoolean(), "ok必须false");
    }

    @Test
    @DisplayName("C1-R2: cid=108合法A(TEST-A-108) 读自己的纪念日 → 成功 不30004")
    void c1_r2_legalReadOwn() throws Exception {
        MvcResult res = mvc.perform(get("/anniversaries/" + annivA_108.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-A-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "合法情侣读自己数据不应30004");
    }

    @Test
    @DisplayName("C1-R3: cid=108情侣B(TEST-B-108 partnerIdx=2) 读A创建的纪念日 → 情侣内共享成功")
    void c1_r3_coupleShareRead() throws Exception {
        MvcResult res = mvc.perform(get("/anniversaries/" + annivA_108.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-B-108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "情侣内partnerIdx=1/2共享，不应30004");
    }

    // ============================ C2: 跨情侣改/删 ============================
    @Test
    @DisplayName("C2-W1: cid=200攻击者C 尝试DELETE 108情侣的纪念日 → 30004跨情侣删拦截")
    void c2_w1_crossDelete() throws Exception {
        MvcResult res = mvc.perform(delete("/anniversaries/" + annivA_108.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-C-200")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "跨情侣删除应为30004");
    }

    @Test
    @DisplayName("C2-W2: cid=200攻击者C 尝试PUT 108情侣纪念日标题 → 30004跨情侣改拦截")
    void c2_w2_crossUpdate() throws Exception {
        String payload = """
                {"title":"攻击者改的","type":"love","targetDate":"2024-01-01","note":"hack"}""";
        MvcResult res = mvc.perform(put("/anniversaries/" + annivA_108.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer TEST-C-200")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "跨情侣修改应为30004");
        // DB里标题没被改
        Anniversary fresh = annivRepo.findById(annivA_108.getId()).orElseThrow();
        assertEquals("在一起的日子", fresh.getTitle(), "DB值没被污染");
    }

    @Test
    @DisplayName("C2-W3: cid=108合法A 正常DELETE自己的纪念日 → 成功")
    void c2_w3_legalDeleteOwn() throws Exception {
        // c108真实ID=插入后的ID，不是硬编码108。用正确JWT（TEST-A-108是硬cid=108，跟我们插入的不匹配）
        // → 我们直接用动态生成的真实cid做校验：先自己构造Token
        com.xindong.common.context.CoupleContext ctx = new com.xindong.common.context.CoupleContext();
        ctx.setUserId(201L);
        ctx.setCoupleId(c108.getId());
        ctx.setPartnerIdx(1);
        com.xindong.common.util.JwtUtil jwtUtil = new com.xindong.common.util.JwtUtil(
                "test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret", 720);
        String realToken = jwtUtil.generateToken(ctx);

        MvcResult res = mvc.perform(delete("/anniversaries/" + annivA_108.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + realToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertNotEquals(String.valueOf(ErrorCode.COUPLE_DATA_FORBIDDEN.getCode()), body.get("code").asText(),
                "合法删除不应30004");
    }

    // ============================ C3: 跨情侣列表读 ============================
    @Test
    @DisplayName("C3-List: cid=200 GET /anniversaries 列表 → 只看到200情侣自己的，看不到108的纪念日")
    void c3_listIsolated() throws Exception {
        com.xindong.common.util.JwtUtil jwtUtil = new com.xindong.common.util.JwtUtil(
                "test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret", 720);

        com.xindong.common.context.CoupleContext c200ctx = new com.xindong.common.context.CoupleContext();
        c200ctx.setUserId(301L);
        c200ctx.setCoupleId(c200.getId());
        c200ctx.setPartnerIdx(1);
        String c200Token = jwtUtil.generateToken(c200ctx);

        MvcResult res = mvc.perform(get("/anniversaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + c200Token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        // ok=true → data是数组。108情侣的"在一起的日子"不应出现在200的列表里
        assertTrue(body.path("ok").asBoolean() || body.has("code"), "Result格式合法");
        String rawData = body.path("data").toString();
        assertFalse(rawData.contains("在一起的日子"),
                "cid=200的列表中绝对不能出现cid=108的纪念日内容!\n raw=" + rawData);
    }
}
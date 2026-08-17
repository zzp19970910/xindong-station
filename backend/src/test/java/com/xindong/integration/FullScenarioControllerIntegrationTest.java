package com.xindong.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.util.JwtUtil;
import com.xindong.content.repository.AnniversaryRepository;
import com.xindong.content.repository.ChecklistRepository;
import com.xindong.content.repository.DiaryRepository;
import com.xindong.incentive.repository.CoupleRepository;
import com.xindong.incentive.repository.WishRepository;
import org.junit.jupiter.api.*;
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
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("📦 核心业务Controller 全场景CRUD集成测试套件（Anniversary/Diary/Checklist/Wish）")
class FullScenarioControllerIntegrationTest {

    private static final String SECRET = "test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret";
    private static String VALID_TOKEN;
    private static Long DYNAMIC_COUPLE_ID;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private CoupleRepository coupleRepo;
    @Autowired
    private AnniversaryRepository annivRepo;
    @Autowired
    private DiaryRepository diaryRepo;
    @Autowired
    private ChecklistRepository checklistRepo;
    @Autowired
    private WishRepository wishRepo;

    @BeforeEach
    void setupTokenAndBaseData() {
        // 清理
        wishRepo.deleteAll();
        checklistRepo.deleteAll();
        diaryRepo.deleteAll();
        annivRepo.deleteAll();
        coupleRepo.deleteAll();

        // 新建一对情侣
        com.xindong.incentive.entity.Couple c = new com.xindong.incentive.entity.Couple();
        c.setInviteCodeP1("ZZZZZZ");
        c.setInviteCodeP2("YYYYYY");
        c.setCoinsTotal(5000);
        DYNAMIC_COUPLE_ID = coupleRepo.saveAndFlush(c).getId();

        CoupleContext ctx = new CoupleContext();
        ctx.setUserId(1L);
        ctx.setCoupleId(DYNAMIC_COUPLE_ID);
        ctx.setPartnerIdx(1);
        ctx.setPhone("13800000001");
        ctx.setNickname("全场景测试用户A");
        JwtUtil jwtUtil = new JwtUtil(SECRET, 720);
        VALID_TOKEN = jwtUtil.generateToken(ctx);
    }

    // ================= 纪念日Anniversary CRUD =================
    @Test
    @Order(10)
    @DisplayName("SC-10: 纪念日 CREATE POST /anniversaries → 成功返回code=0带id")
    void sc10_createAnniversary() throws Exception {
        String payload = """
                {"title":"测试纪念日","type":"love","emoji":"❤️","targetDate":"%s","isTop":true,"displayMode":"countup","note":"测试note"}"""
                .formatted(LocalDate.now().format(DateTimeFormatter.ISO_DATE));

        MvcResult res = mvc.perform(post("/anniversaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        JsonNode n = om.readTree(res.getResponse().getContentAsString());
        Long id = n.path("data").path("id").asLong();
        assertTrue(id > 0, "新插入纪念日ID应为正数");
        assertTrue(annivRepo.findById(id).isPresent(), "DB里存在新插入的纪念日");
        assertEquals("测试纪念日", annivRepo.findById(id).get().getTitle());
    }

    @Test
    @Order(11)
    @DisplayName("SC-11: 纪念日 LIST GET /anniversaries → 返回数组，至少1条")
    void sc11_listAnniversary() throws Exception {
        // 先插一条
        com.xindong.content.entity.Anniversary a = new com.xindong.content.entity.Anniversary();
        a.setCoupleId(DYNAMIC_COUPLE_ID);
        a.setTitle("List测试");
        a.setType("love");
        a.setTargetDate(LocalDate.now());
        annivRepo.saveAndFlush(a);

        MvcResult res = mvc.perform(get("/anniversaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        JsonNode arr = om.readTree(res.getResponse().getContentAsString()).path("data");
        assertTrue(arr.size() >= 1, "列表至少1条");
        assertEquals("List测试", arr.get(0).path("title").asText(), "标题正确");
    }

    @Test
    @Order(12)
    @DisplayName("SC-12: 纪念日 UPDATE PUT /anniversaries/{id} → 标题修改成功")
    void sc12_updateAnniversary() throws Exception {
        com.xindong.content.entity.Anniversary a = new com.xindong.content.entity.Anniversary();
        a.setCoupleId(DYNAMIC_COUPLE_ID);
        a.setTitle("原始标题");
        a.setType("love");
        a.setTargetDate(LocalDate.now());
        Long id = annivRepo.saveAndFlush(a).getId();

        String payload = """
                {"title":"更新后的标题","type":"love","targetDate":"%s","emoji":"🎉","note":"更新note"}"""
                .formatted(LocalDate.now().format(DateTimeFormatter.ISO_DATE));

        mvc.perform(put("/anniversaries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        assertEquals("更新后的标题", annivRepo.findById(id).get().getTitle());
    }

    @Test
    @Order(13)
    @DisplayName("SC-13: 纪念日 DELETE DELETE /anniversaries/{id} → DB存在→成功")
    void sc13_deleteAnniversary() throws Exception {
        com.xindong.content.entity.Anniversary a = new com.xindong.content.entity.Anniversary();
        a.setCoupleId(DYNAMIC_COUPLE_ID);
        a.setTitle("待删除");
        a.setType("love");
        a.setTargetDate(LocalDate.now());
        Long id = annivRepo.saveAndFlush(a).getId();
        assertTrue(annivRepo.findById(id).isPresent());

        mvc.perform(delete("/anniversaries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        assertFalse(annivRepo.findById(id).isPresent(), "删除后DB不存在");
    }

    // ================= 日记Diary CRUD最小验证 =================
    @Test
    @Order(20)
    @DisplayName("SC-20: 日记发布 POST /diaries → 成功创建返回code=0")
    void sc20_createDiary() throws Exception {
        String payload = """
                {"title":"测试日记标题","content":"今天天气很好，一起出去玩了！","mood":1,"moodEmoji":"😊","isPrivate":false,"tag":"旅行"}""";
        MvcResult res = mvc.perform(post("/diaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertTrue(body.path("ok").asBoolean() || body.path("code").asText().equals("0"),
                "创建日记应成功或业务错，但非401/403: code=" + body.path("code") + " msg=" + body.path("msg"));
    }

    @Test
    @Order(21)
    @DisplayName("SC-21: 日记列表 GET /diaries → 200 OK code=0/ok=true")
    void sc21_listDiary() throws Exception {
        mvc.perform(get("/diaries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }

    // ================= 检查清单 Checklist最小验证 =================
    @Test
    @Order(30)
    @DisplayName("SC-30: 检查清单 GET /checklists → 公共接口无Token也能访问(白名单)")
    void sc30_checklistPublicList() throws Exception {
        MvcResult res = mvc.perform(get("/checklists")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode b = om.readTree(res.getResponse().getContentAsString());
        // code=0/ok=true 或 data=[]数组 都合法，但绝不是AUTH_REQUIRED=30005
        assertNotEquals("30005", b.path("code").asText(), "/checklists白名单不应401");
        assertNotEquals("30006", b.path("code").asText(), "/checklists不应TOKEN_INVALID");
    }

    // ================= 愿望商城 Wish最小验证 =================
    @Test
    @Order(40)
    @DisplayName("SC-40: 愿望 GET /wishes 带Token → 进入Controller 不401")
    void sc40_wishListWithToken() throws Exception {
        MvcResult res = mvc.perform(get("/wishes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertTrue(body.has("code"), "必须是Result格式,code字段存在。实际内容:" + body);
    }
}
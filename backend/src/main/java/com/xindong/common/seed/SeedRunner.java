package com.xindong.common.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.xindong.common.seed.SeedDataConstants.*;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SeedRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        long t0 = System.currentTimeMillis();
        try {
            try { seedQuizQuestions(); } catch (Throwable e) { log.warn("[Seed] quiz_questions初始化跳过(不影响服务启动): {}", e.getMessage()); }
            try { seedChecklist();    } catch (Throwable e) { log.warn("[Seed] checklists预置初始化跳过(不影响服务启动): {}", e.getMessage()); }
            try { seedRedlineData();  } catch (Throwable e) { log.warn("[Seed] 红线专用数据初始化跳过(不影响服务启动): {}", e.getMessage()); }
        } catch (Throwable t) {
            log.error("[Seed] 初始化全流程出现未预期异常(已强制吞掉，不影响服务启动): {}", t.getMessage(), t);
        } finally {
            log.info("[Seed初始化] 耗时={}ms，无论初始化是否成功，服务都会正常启动对外提供服务", System.currentTimeMillis() - t0);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName + " LIMIT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.info("[Seed] 表{}不存在或Hibernate未完成建表，跳过该类初始化: {}", tableName, e.getMessage());
            return false;
        }
    }

    private boolean isPostgres() {
        try {
            String url = jdbc.getDataSource().getConnection().getMetaData().getURL();
            return url != null && url.toLowerCase().startsWith("jdbc:postgresql");
        } catch (Exception ignore) { return false; }
    }

    private void seedRedlineData() {
        final boolean pg = isPostgres();
        final String now = pg ? "CURRENT_TIMESTAMP" : "NOW()";

        if (!tableExists("couples")) return;
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM couples WHERE id IN (108,200,909)", Integer.class);
        if (cnt != null && cnt >= 3) {
            log.info("[Seed] 红线专用couples已存在(108/200/909) 跳过初始化");
        } else {
            String fmt = pg
                ? "INSERT INTO couples(id, together_date, invite_code_p1, invite_code_p2, coins_total, theme, sign_streak, version) VALUES (%d,'%s','%s','%s',%d,'%s',%d,%d) ON CONFLICT (id) DO NOTHING"
                : "INSERT IGNORE INTO couples(id, together_date, invite_code_p1, invite_code_p2, coins_total, theme, sign_streak, version) VALUES (%d,'%s','%s','%s',%d,'%s',%d,%d)";
            jdbc.update(String.format(fmt, 909,"2024-01-01","RED909","RED910",100,"default",0,0));
            jdbc.update(String.format(fmt, 108,"2024-06-01","RED108","RED109",1000,"default",0,0));
            jdbc.update(String.format(fmt, 200,"2025-01-01","RED200","RED201",1000,"default",0,0));
            log.info("[Seed] 红线专用couples插入完成 108(1000币/正常) 200(1000币/攻击者) 909(100币/穷情侣)");
        }

        if (!tableExists("users")) return;
        Integer ucnt = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id IN (201,202,301,302,20101,20202)", Integer.class);
        if (ucnt != null && ucnt >= 6) {
            log.info("[Seed] 红线专用users已存在(6条) 跳过初始化");
        } else {
            String fmt = pg
                ? "INSERT INTO users(id, phone, nickname, avatar_url, couple_id, partner_idx) VALUES (%d,'%s','%s','%s',%d,%d) ON CONFLICT (id) DO NOTHING"
                : "INSERT IGNORE INTO users(id, phone, nickname, avatar_url, couple_id, partner_idx) VALUES (%d,'%s','%s','%s',%d,%d)";
            jdbc.update(String.format(fmt, 20101,"13800002101","红线909-A","emoji:❤️#FFB6C1",909,1));
            jdbc.update(String.format(fmt, 20202,"13800002102","红线909-B","emoji:💙#87CEEB",909,2));
            jdbc.update(String.format(fmt, 201,"13800000201","红线108-A","emoji:🌸#FF69B4",108,1));
            jdbc.update(String.format(fmt, 202,"13800000202","红线108-B","emoji:🌷#DA70D6",108,2));
            jdbc.update(String.format(fmt, 301,"13800000301","红线200-攻击者C","emoji:👿#FF4500",200,1));
            jdbc.update(String.format(fmt, 302,"13800000302","红线200-同伴D","emoji:😈#8B0000",200,2));
            log.info("[Seed] 红线专用users插入完成 uid20101/20202(c909) uid201/202(c108) uid301/302(c200攻击者)");
        }

        if (!tableExists("coin_logs")) return;
        Integer icnt = jdbc.queryForObject("SELECT COUNT(*) FROM coin_logs WHERE couple_id IN (108,200,909) AND reason='INIT_BALANCE'", Integer.class);
        if (icnt != null && icnt >= 3) {
            log.info("[Seed] INIT_BALANCE流水已存在(3条) 跳过初始化");
        } else {
            String today = java.time.LocalDate.now().toString();
            java.time.LocalDateTime nowDt = java.time.LocalDateTime.now();
            String sql = pg
                ? "INSERT INTO coin_logs(couple_id, reason, reason_label, delta, balance_after, from_user_id, from_partner, biz_id, date_str, created_at) VALUES (%d,'%s','%s',%d,%d,NULL,NULL,'%s','%s','%s') ON CONFLICT DO NOTHING"
                : "INSERT IGNORE INTO coin_logs(couple_id, reason, reason_label, delta, balance_after, from_user_id, from_partner, biz_id, date_str, created_at) VALUES (%d,'%s','%s',%d,%d,NULL,NULL,'%s','%s','%s')";
            String nowStr = nowDt.toString().replace("T"," ");
            jdbc.update(String.format(sql, 909,"INIT_BALANCE","情侣初始余额入账",100,100,"seed_init_909",today,nowStr));
            jdbc.update(String.format(sql, 108,"INIT_BALANCE","情侣初始余额入账",1000,1000,"seed_init_108",today,nowStr));
            jdbc.update(String.format(sql, 200,"INIT_BALANCE","情侣初始余额入账",1000,1000,"seed_init_200",today,nowStr));
            log.info("[Seed] INIT_BALANCE流水插入完成 c909=100 c108=1000 c200=1000 B1对账diff=0");
        }

        if (tableExists("diaries")) {
            Integer diaryCnt = jdbc.queryForObject("SELECT COUNT(*) FROM diaries WHERE id=441", Integer.class);
            if (diaryCnt == null || diaryCnt == 0) {
                String sql = String.format(
                    "INSERT INTO diaries(id, couple_id, user_id, content, mood, record_date, created_at, updated_at) VALUES (441,108,201,'[红线专用]今天和TA一起看了日落，超开心，期待下次约会！',5,'%s',%s,%s)",
                    java.time.LocalDate.now(), now, now);
                jdbc.update(sql);
                log.info("[Seed] 红线日记441插入完成 c108 author=201 非作者删C3a 跨情侣读C3b");
            }
        }

        if (tableExists("checklists")) {
            Integer chkCnt = jdbc.queryForObject("SELECT COUNT(*) FROM checklists WHERE id=99", Integer.class);
            if (chkCnt == null || chkCnt == 0) {
                final Object isPreset = pg ? Boolean.FALSE : 0;
                final Object isDone = pg ? Boolean.FALSE : 0;
                String sql = String.format(
                    "INSERT INTO checklists(id, couple_id, sort_order, title, is_preset, is_done, category, description, icon, milestone_bonus, created_at, updated_at) VALUES (99,108,99,'[红线C5]一起完成薅羊毛清单',%s,%s,'日常','C标记A的清单不能加30币','✅',0,%s,%s)",
                    isPreset, isDone, now, now);
                jdbc.update(sql);
                log.info("[Seed] 红线清单99插入完成 c108 C5薅羊毛");
            }
        }

        if (tableExists("wishes")) {
            Integer wishCnt = jdbc.queryForObject("SELECT COUNT(*) FROM wishes WHERE id=888", Integer.class);
            if (wishCnt == null || wishCnt == 0) {
                String sql = String.format(
                    "INSERT INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at) VALUES (888,909,'[红线B6B7]穷情侣兑换贵愿望测试',666,'redline_wish.png',20101,'PENDING_APPROVAL','[{\"name\":\"执行兑换\",\"done\":false}]',1,0,%s,%s)",
                    now, now);
                jdbc.update(sql);
                log.info("[Seed] 红线愿望888插入完成 c909 price=666 c909只有100币 B6余额不足拦截 B7并发1成1败");
            }
        }

        if (tableExists("anniversaries")) {
            Integer annivCnt = jdbc.queryForObject("SELECT COUNT(*) FROM anniversaries WHERE id=9999", Integer.class);
            if (annivCnt == null || annivCnt == 0) {
                String sql = String.format(
                    "INSERT INTO anniversaries(id, couple_id, title, target_date, type, emoji, created_at, updated_at) VALUES (9999,108,'[红线C1C2]百日纪念日测试','2024-09-01','milestone','🎉',%s,%s)",
                    now, now);
                jdbc.update(sql);
                log.info("[Seed] 红线纪念日9999插入完成 c108 C1跨情侣读 C2跨情侣删 404+30004");
            }
        }

        if (tableExists("love_letters")) {
            Integer letterCnt = jdbc.queryForObject("SELECT COUNT(*) FROM love_letters WHERE id=772", Integer.class);
            if (letterCnt == null || letterCnt == 0) {
                final Object isTimeCapsule = pg ? Boolean.FALSE : 0;
                String cipherPlaceholder = "REDLINE_SEED_CIPHER_PLACEHOLDER_772::" + java.util.Base64.getEncoder().encodeToString("[红线C4]亲爱的，谢谢你一直以来的陪伴，这是红线测试专用信件内容不能被C读到。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jdbc.update(String.format(
                    "INSERT INTO love_letters(id, couple_id, sender_id, receiver_id, content_cipher, is_time_capsule, created_at) VALUES (772,108,201,202,'%s',%s,%s)",
                    cipherPlaceholder.replace("'", "''"), isTimeCapsule, now));
                log.info("[Seed] 红线信件772插入完成 c108 A→B C4跨情侣读 404+30004 (cipher占位可正常详情接口访问)");
            }
        }

        log.info("[Seed] 红线专用数据初始化流程完成(缺表自动跳过，不影响启动)");
    }

    private void seedQuizQuestions() {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions", Integer.class);
        } catch (Exception e) {
            log.info("[Seed] quiz_questions表不存在(Hibernate未建表或无Entity)，跳过初始化: {}", e.getMessage());
            return;
        }
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions", Integer.class);
        if (cnt != null && cnt > 0) {
            log.info("[Seed] quiz_questions 已有{}条 跳过初始化", cnt);
            return;
        }
        final boolean pg = isPostgres();
        final Object isPreset = pg ? Boolean.TRUE : 1;
        String sql = "INSERT INTO quiz_questions(content, option_a, option_b, option_c, option_d, is_preset) VALUES (?,?,?,?,?," + (pg ? "?" : "1") + ")";
        List<Object[]> batch = new ArrayList<>(QuizQuestions.QUESTION_COUNT);
        for (int i = 0; i < QuizQuestions.QUESTION_COUNT; i++) {
            String[] q = QuizQuestions.RAW.get(i);
            batch.add(pg
                    ? new Object[]{q[0], q[1], q[2], q[3], q[4], isPreset}
                    : new Object[]{q[0], q[1], q[2], q[3], q[4]});
        }
        int[] res = jdbc.batchUpdate(sql, batch);
        int ok = 0;
        for (int r : res) if (r > 0) ok++;
        log.info("[Seed] quiz_questions 批量插入完成 预期={} 成功={}", QuizQuestions.QUESTION_COUNT, ok);
    }

    private void seedChecklist() {
        if (!tableExists("checklists")) return;
        final boolean pg = isPostgres();
        final String now = pg ? "CURRENT_TIMESTAMP" : "NOW()";
        final Object isPreset = pg ? Boolean.TRUE : 1;
        final Object isDone = pg ? Boolean.FALSE : 0;
        final String whereIsPreset = pg ? "is_preset=?" : "is_preset=1";
        Integer cnt = pg
                ? jdbc.queryForObject("SELECT COUNT(*) FROM checklists WHERE couple_id IS NULL AND is_preset=?", Integer.class, isPreset)
                : jdbc.queryForObject("SELECT COUNT(*) FROM checklists WHERE couple_id IS NULL AND is_preset=1", Integer.class);
        if (cnt != null && cnt > 0) {
            log.info("[Seed] checklists 预置模板已有{}条 跳过初始化", cnt);
            return;
        }
        String sql = String.format(
            "INSERT INTO checklists(couple_id, sort_order, title, is_preset, is_done, category, description, icon, milestone_bonus, created_at, updated_at) VALUES (NULL,?,?,?,?,?,?,?,?,%s,%s)",
            now, now);
        List<Object[]> batch = new ArrayList<>(ChecklistPreset.ITEM_COUNT);
        for (int i = 0; i < ChecklistPreset.ITEM_COUNT; i++) {
            batch.add(new Object[]{
                    ChecklistPreset.sortAt(i),
                    ChecklistPreset.titleAt(i),
                    isPreset,
                    isDone,
                    ChecklistPreset.categoryAt(i),
                    ChecklistPreset.descriptionAt(i),
                    ChecklistPreset.iconAt(i),
                    ChecklistPreset.milestoneBonusAt(i)
            });
        }
        int[] res = jdbc.batchUpdate(sql, batch);
        int ok = 0;
        for (int r : res) if (r > 0) ok++;
        log.info("[Seed] checklists 预置模板插入 预期={} 成功={} category/description/icon/milestone_bonus 全列同步",
                ChecklistPreset.ITEM_COUNT, ok);
    }
}
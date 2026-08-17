package com.xindong.interactive.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.seed.SeedDataConstants;
import com.xindong.interactive.entity.IcebreakSession;
import com.xindong.interactive.entity.IcebreakTaskRecord;
import com.xindong.interactive.repository.IcebreakSessionRepository;
import com.xindong.interactive.repository.IcebreakTaskRecordRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 批次7 M07 破冰转盘（3接口）
 *   spin()       → 今日剩3次+ 拦41102；随机抽1任务Seed预置（和清单/题库同源100条破冰动作）
 *   submit(id,reflection) → 当前已抽的任务提交完成 + spinsLeft+2（下次可再转2次）+ ICEBREAK_TASK +3
 *   history(page,size) → 完成历史列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcebreakService {

    private final IcebreakSessionRepository sessionRepo;
    private final IcebreakTaskRecordRepository recordRepo;
    private final CoinService coinService;

    private static final int DAILY_FREE = 3;
    private static final int BONUS_SPINS_PER_TASK = 2;
    private static final int MAX_DAILY_SPINS = 6;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * M07-0 初始化今日会话（兼容脚本 /icebreak/start；category忽略）
     * 返回 sessionId = IB-{cid}-{date}-0000，后续 /{sessionId}/roll 必须和当前coupleId一致，否则 30004/404
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startSession(Map<String, Object> body) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        String today = LocalDate.now().toString();
        IcebreakSession s = sessionRepo.findByCoupleIdAndDateStr(coupleId, today)
                .orElseGet(() -> {
                    IcebreakSession init = new IcebreakSession();
                    init.setCoupleId(coupleId);
                    init.setDateStr(today);
                    init.setSpinsLeft(DAILY_FREE);
                    init.setCreatedAt(LocalDateTime.now());
                    init.setUpdatedAt(init.getCreatedAt());
                    return sessionRepo.save(init);
                });
        boolean dirty = false;
        if (s.getSpinsLeft() == null || s.getSpinsLeft() < 0 || s.getSpinsLeft() > MAX_DAILY_SPINS) {
            s.setSpinsLeft(cap(s.getSpinsLeft() == null ? DAILY_FREE : s.getSpinsLeft()));
            dirty = true;
        }
        if (dirty) { s.setUpdatedAt(LocalDateTime.now()); sessionRepo.save(s); }
        String sid = buildSessionId(coupleId, today);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sessionId", sid);
        res.put("session_id", sid);
        res.put("spinsLeft", cap(s.getSpinsLeft()));
        res.put("spinTodayLeft", cap(s.getSpinsLeft()));
        res.put("maxDaily", MAX_DAILY_SPINS);
        res.put("date", today);
        return res;
    }

    private static int cap(int n) { return Math.max(0, Math.min(MAX_DAILY_SPINS, n)); }

    /**
     * M07-状态 查询今日状态（剩余次数 / 当前未完成任务）
     * 前端 onMounted 先调这个，不要用 left=3 写死默认值
     */
    @Transactional(readOnly = true)
    public Map<String, Object> todayState() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        String today = LocalDate.now().toString();
        Map<String, Object> res = new LinkedHashMap<>();
        IcebreakSession s = sessionRepo.findByCoupleIdAndDateStr(coupleId, today).orElse(null);
        int left = (s != null && s.getSpinsLeft() != null) ? cap(s.getSpinsLeft()) : DAILY_FREE;
        // DB脏数据（旧bug遗留的>6）立即回写修正，避免下次再读错
        if (s != null && (s.getSpinsLeft() == null || s.getSpinsLeft() > MAX_DAILY_SPINS || s.getSpinsLeft() < 0)) {
            try {
                s.setSpinsLeft(left);
                s.setUpdatedAt(LocalDateTime.now());
                sessionRepo.save(s);
            } catch (Exception ignore) {}
        }
        res.put("spinsLeft", left);
        res.put("spinTodayLeft", left);
        res.put("dailyFree", DAILY_FREE);
        res.put("maxDaily", MAX_DAILY_SPINS);
        res.put("date", today);
        res.put("sessionId", buildSessionId(coupleId, today));
        if (s != null && s.getCurrentTaskId() != null) {
            Map<String, Object> cur = new LinkedHashMap<>();
            cur.put("id", s.getCurrentTaskId());
            cur.put("title", s.getCurrentTaskName());
            cur.put("task", s.getCurrentTaskName());
            cur.put("description", "完成后获得 +" + BONUS_SPINS_PER_TASK + " 次抽奖机会 + 3💰 内容金币");
            cur.put("category", "进行中");
            cur.put("difficulty", 2);
            cur.put("bonusCoins", 3);
            cur.put("timeMin", 10);
            cur.put("emoji", "🎯");
            res.put("task", cur);
            res.put("currentTaskId", s.getCurrentTaskId());
            res.put("currentTaskName", s.getCurrentTaskName());
            res.put("hasUnfinished", true);
        } else {
            res.put("task", null);
            res.put("hasUnfinished", false);
        }
        return res;
    }

    /**
     * M07-1 转一次转盘
     * 幂等：每日session不存在→spinsLeft=3建1条
     * @param sessionId 可选：传了则必须"当前coupleId + IB-cid-date-*"匹配，跨情侣→30004/404（C7红线）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> spin(String sessionId) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        String today = LocalDate.now().toString();
        // 🔴C7红线：如果传了sessionId，必须做跨情侣隔离校验
        if (sessionId != null && !sessionId.isBlank()) {
            String expectedPrefix = "IB-" + coupleId + "-" + today + "-";
            String expectedLegacy = "IB-" + coupleId + "-" + today;
            if (!sessionId.startsWith(expectedPrefix) && !sessionId.equals(expectedLegacy)) {
                throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN, "破冰会话不存在或已过期 sessionId=" + sessionId);
            }
        }
        IcebreakSession s = sessionRepo.findByCoupleIdAndDateStr(coupleId, today)
                .orElseGet(() -> {
                    IcebreakSession init = new IcebreakSession();
                    init.setCoupleId(coupleId);
                    init.setDateStr(today);
                    init.setSpinsLeft(DAILY_FREE);
                    init.setCreatedAt(LocalDateTime.now());
                    init.setUpdatedAt(init.getCreatedAt());
                    return sessionRepo.save(init);
                });
        // 读DB前先cap脏数据（旧bug遗留>6或负数）
        boolean dirty = false;
        if (s.getSpinsLeft() == null || s.getSpinsLeft() < 0 || s.getSpinsLeft() > MAX_DAILY_SPINS) {
            s.setSpinsLeft(cap(s.getSpinsLeft() == null ? DAILY_FREE : s.getSpinsLeft()));
            dirty = true;
        }
        // 🔴21102 没次数了
        if (s.getSpinsLeft() <= 0) throw new BusinessException(ErrorCode.ICEBREAK_NO_SPINS);
        // 🔴21103 当前还有没完成的任务 → 附data让前端直接显示卡片不再弹"明天再来"
        if (s.getCurrentTaskId() != null) {
            Map<String, Object> cur = todayState();
            throw new BusinessException(ErrorCode.ICEBREAK_TASK_NOT_DONE,
                    "请先完成当前任务[" + s.getCurrentTaskName() + "]", cur);
        }
        // 扣1次spin（扣完再cap一次防负数）
        s.setSpinsLeft(cap(s.getSpinsLeft() - 1));
        dirty = true;
        // 从SeedDataConstants随机选1条破冰任务（210题后面扩展的破冰语料，没有则临时取默认动作库）
        List<Map<String, Object>> bank = SeedDataConstants.ICEBREAK_TASKS;
        if (bank == null || bank.isEmpty()) bank = defaultTasks();
        Random r = new Random(System.currentTimeMillis());
        Map<String, Object> raw = bank.get(r.nextInt(bank.size()));
        Long taskId = ((Number) raw.get("id")).longValue();
        String taskName = String.valueOf(raw.get("task"));
        s.setCurrentTaskId(taskId);
        s.setCurrentTaskName(taskName);
        s.setUpdatedAt(LocalDateTime.now());
        if (dirty) sessionRepo.save(s);
        else sessionRepo.save(s);

        int difficulty = raw.get("difficulty") != null ? ((Number) raw.get("difficulty")).intValue() : 2;
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", taskId);
        task.put("title", taskName);
        task.put("task", taskName);
        task.put("description", String.valueOf(raw.getOrDefault("description",
                "一起完成这个小挑战，让爱升温～完成后获得 +" + BONUS_SPINS_PER_TASK + "次抽奖 + 3💰")));
        task.put("category", raw.getOrDefault("category", "日常"));
        task.put("difficulty", difficulty);
        task.put("bonusCoins", 3);
        task.put("timeMin", 5 + difficulty * 5);
        task.put("emoji", emojiOfCategory(String.valueOf(task.get("category"))));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", taskId);
        res.put("taskName", taskName);
        res.put("task", task);
        res.put("category", raw.get("category"));
        res.put("difficulty", difficulty);
        res.put("segment", r.nextInt(9));
        res.put("spinsLeftAfter", cap(s.getSpinsLeft()));
        res.put("spinTodayLeft", cap(s.getSpinsLeft()));
        res.put("spinsLeft", cap(s.getSpinsLeft()));
        res.put("maxDaily", MAX_DAILY_SPINS);
        res.put("sessionId", buildSessionId(coupleId, today));
        res.put("session_id", buildSessionId(coupleId, today));
        return res;
    }

    private String emojiOfCategory(String cat) {
        if (cat == null) return "🎯";
        switch (cat) {
            case "浪漫": return "💖";
            case "日常": return "🍳";
            case "冒险": case "挑战": return "🎢";
            case "甜蜜": return "🍬";
            case "游戏": return "🎮";
            case "旅行": return "✈️";
            case "感动": case "暖心": return "😊";
            case "惊喜": return "🎁";
            default: return "🎯";
        }
    }

    /**
     * 生成确定性 sessionId：IB-{cid}-{YYYY-MM-DD}-0000
     * 脚本C7会假装把A的sid塞给C去roll → 前缀cid=108 vs C的cid=909 不匹配 → 30004/404拦截
     */
    private String buildSessionId(Long cid, String today) {
        return "IB-" + cid + "-" + today + "-0000";
    }

    /**
     * M07-2 提交完成当前任务（写感悟+加2spin+加3金币）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long taskId, String reflection) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        String today = LocalDate.now().toString();
        IcebreakSession s = sessionRepo.findByCoupleIdAndDateStr(coupleId, today)
                .orElseThrow(() -> new BusinessException(ErrorCode.ICEBREAK_SESSION_NOT_FOUND));
        if (s.getCurrentTaskId() == null)
            throw new BusinessException(ErrorCode.ICEBREAK_TASK_NOT_DONE, "当前没有待完成任务，先spin再抽");
        if (!s.getCurrentTaskId().equals(taskId))
            throw new BusinessException(ErrorCode.ICEBREAK_TASK_NOT_DONE, "taskId与当前待完成不一致");
        // 感悟长度41104校验
        if (reflection != null && reflection.length() > 500)
            throw new BusinessException(ErrorCode.ICEBREAK_TASK_TOO_LONG);
        // 写record
        IcebreakTaskRecord r = new IcebreakTaskRecord();
        r.setCoupleId(coupleId);
        r.setTaskId(s.getCurrentTaskId());
        r.setTaskName(s.getCurrentTaskName());
        r.setFinishedById(uid);
        r.setPartnerIdx(pIdx);
        r.setReflection(reflection);
        r.setCreatedAt(LocalDateTime.now());
        recordRepo.save(r);
        // 清掉session的当前任务 + spinsLeft + BONUS_SPINS_PER_TASK(2)，每日最多 MAX_DAILY_SPINS(6) 避免无限膨胀
        s.setCurrentTaskId(null);
        s.setCurrentTaskName(null);
        s.setSpinsLeft(cap(s.getSpinsLeft() == null ? BONUS_SPINS_PER_TASK : s.getSpinsLeft() + BONUS_SPINS_PER_TASK));
        s.setUpdatedAt(LocalDateTime.now());
        sessionRepo.save(s);
        // +3金币 ICEBREAK_TASK
        try {
            coinService.addCoins(coupleId, CoinReason.ICEBREAK_TASK, null, uid, null,
                    "icebreak_task:" + r.getId());
        } catch (Exception e) {
            log.warn("[破冰完成送3金币失败 不影响提交] cid={} err={}", coupleId, e.getMessage());
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("recordId", r.getId());
        res.put("taskName", r.getTaskName());
        res.put("bonusSpins", BONUS_SPINS_PER_TASK);
        res.put("spinsLeftNow", cap(s.getSpinsLeft()));
        res.put("spinTodayLeft", cap(s.getSpinsLeft()));
        res.put("spinsLeft", cap(s.getSpinsLeft()));
        res.put("maxDaily", MAX_DAILY_SPINS);
        res.put("bonusCoins", 3);
        res.put("bonus", 3);
        // 前端期望 record: IcebreakHistory 结构
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", r.getId());
        rec.put("coupleId", r.getCoupleId());
        rec.put("taskId", r.getTaskId());
        rec.put("taskTitle", r.getTaskName());
        rec.put("taskEmoji", emojiOfTaskTitle(r.getTaskName()));
        rec.put("status", "DONE");
        rec.put("bonusCoins", 3);
        rec.put("note", r.getReflection());
        rec.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().format(DTF));
        rec.put("doneAt", rec.get("createdAt"));
        res.put("record", rec);
        return res;
    }

    /**
     * M07-3 破冰完成历史
     */
    @Transactional(readOnly = true)
    public Map<String, Object> history(int page, int size) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        size = Math.min(50, Math.max(5, size));
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size);
        var p = recordRepo.findByCoupleIdOrderByCreatedAtDesc(coupleId, pr);
        List<Map<String, Object>> list = new ArrayList<>();
        for (IcebreakTaskRecord r : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("taskId", r.getTaskId());
            m.put("taskName", r.getTaskName());
            m.put("taskTitle", r.getTaskName());
            m.put("taskEmoji", emojiOfTaskTitle(r.getTaskName()));
            m.put("finishedById", r.getFinishedById());
            m.put("partnerIdx", r.getPartnerIdx());
            m.put("reflection", r.getReflection());
            m.put("note", r.getReflection());
            m.put("status", "DONE");
            m.put("bonusCoins", 3);
            m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().format(DTF));
            m.put("doneAt", m.get("createdAt"));
            list.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", list);
        res.put("page", page);
        res.put("size", size);
        res.put("total", p.getTotalElements());
        return res;
    }

    private String emojiOfTaskTitle(String title) {
        if (title == null) return "✅";
        String t = title;
        if (t.contains("散步") || t.contains("出门") || t.contains("公园")) return "🚶";
        if (t.contains("早餐") || t.contains("做饭") || t.contains("新菜") || t.contains("整理")) return "🍳";
        if (t.contains("电影") || t.contains("卡拉OK") || t.contains("游戏")) return "🎮";
        if (t.contains("礼物") || t.contains("惊喜") || t.contains("情书") || t.contains("手写")) return "💌";
        if (t.contains("按摩") || t.contains("拥抱") || t.contains("牵手") || t.contains("肩颈")) return "🤗";
        if (t.contains("优点") || t.contains("信任") || t.contains("手机") || t.contains("读")) return "💬";
        if (t.contains("合影") || t.contains("拍") || t.contains("日落") || t.contains("日出")) return "📷";
        if (t.contains("逛街")) return "🛍️";
        return "✅";
    }

    /**
     * 默认破冰任务库（SeedDataConstants没值时兜底 不影响启动）
     */
    private List<Map<String, Object>> defaultTasks() {
        String[] ts = {
                "一起做一次早餐", "给对方写10条优点", "一起去公园散步20分钟", "互赠一个小礼物(<50元)",
                "看一部彼此都没看过的电影", "一起整理衣柜/书架", "拍一张搞怪合影", "一起学做一道新菜",
                "牵手逛街不玩手机1小时", "给对方做5分钟肩颈按摩", "交换手机看5分钟(建立信任)",
                "一起看日落/日出", "一起去唱卡拉OK", "互相读一封手写情书", "一起玩默契问答游戏"
        };
        String[] cats = {"日常", "暖心", "惊喜", "挑战"};
        List<Map<String, Object>> out = new ArrayList<>(ts.length);
        for (int i = 0; i < ts.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("task", ts[i]);
            m.put("category", cats[i % cats.length]);
            m.put("difficulty", (i % 3) + 1);
            out.add(m);
        }
        return out;
    }
}
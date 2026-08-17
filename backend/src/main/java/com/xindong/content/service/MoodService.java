package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.content.entity.Mood;
import com.xindong.content.repository.MoodRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 批次3 M03 心情服务
 * 3个业务接口：打卡 / 列表 / 月历热力图
 * 核心业务：UNIQUE(couple_id+partner_idx+date) 同日不重复
 *          每日首次打卡 +3内容金币（30/50上限）
 *          月历热力图返回 1~30 号 0=无 1=一方 2=双方都打 + 心情主色
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoodService {

    private final MoodRepository moodRepository;
    private final CoinService coinService;

    /**
     * M03-1 今日心情打卡
     * mood_type：1开心/2满足/3一般/4低落/5烦躁/6痛苦（字典枚举）
     * note：备注 最多100字
     * image_url：最多1张图 URL
     * 🔴参数校验必须在UNIQUE冲突前：非法心情50301 > 今日已打卡20301（错误码优先级）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkIn(Integer moodType, String note, String imageUrl) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        if (pIdx == null) throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        // 🔴🔴🔴 M03-005修复：心情值非法→直接抛50301，必须在"今日已打卡"检查之前，保证50301优先级>20301
        if (moodType == null) {
            throw new BusinessException(ErrorCode.MOOD_INVALID);
        }
        if (moodType < 1 || moodType > 6) {
            throw new BusinessException(ErrorCode.MOOD_INVALID);
        }
        if (note != null && note.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "note最多100字");
        }

        LocalDate today = LocalDate.now();
        String dateStr = today.toString();

        Mood m = new Mood();
        m.setCoupleId(coupleId);
        m.setPartnerIdx(pIdx);
        m.setDateStr(dateStr);
        m.setMoodType(moodType);
        m.setNote(note);
        m.setImageUrl(imageUrl);
        m.setUserId(uid);
        m.setCreatedAt(java.time.LocalDateTime.now());
        try {
            moodRepository.save(m);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE索引冲突 → 今日已打卡 20301
            log.warn("[心情重复打卡] cid={} uid={} date={}", coupleId, uid, dateStr);
            throw new BusinessException(ErrorCode.MOOD_ALREADY_TODAY);
        }

        // 打卡送3金币（首次才会触发，后续Unique冲突先走上面返回）
        int coinsDelta = 0;
        try {
            coinsDelta = coinService.addCoins(coupleId, CoinReason.MOOD, null, uid, null, "mood:" + dateStr + ":p" + pIdx);
        } catch (Exception e) {
            log.warn("[心情打卡送币失败 不影响打卡] cid={} err={}", coupleId, e.getMessage());
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("moodId", m.getId());
        res.put("id", m.getId());
        res.put("moodType", moodType);
        res.put("dateStr", dateStr);
        res.put("date", dateStr);
        res.put("partnerIdx", pIdx);
        res.put("userId", uid);
        res.put("note", note);
        res.put("imageUrl", imageUrl);
        res.put("coinsEarned", coinsDelta);

        String emoji; int score;
        switch (moodType) {
            case 1:  emoji = "😊"; score = 10; break;
            case 2:  emoji = "🥰"; score = 8;  break;
            case 3:  emoji = "😐"; score = 6;  break;
            case 4:  emoji = "😢"; score = 4;  break;
            case 5:  emoji = "😠"; score = 2;  break;
            case 6:  emoji = "💔"; score = 1;  break;
            default: emoji = "😊"; score = 8;  break;
        }
        // 用用户实际传的emoji（如果note里前缀是emoji）
        if (note != null && !note.isEmpty()) {
            String t = note.trim();
            if (t.length() >= 2 && Character.isHighSurrogate(t.charAt(0))) {
                emoji = t.substring(0, 2);
            }
        }
        res.put("emoji", emoji);
        res.put("score", score);
        res.put("createdAt", m.getCreatedAt() == null ? null
                : m.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        return res;
    }

    /**
     * 🔴正文测试前置清理：删除今日打卡（解决B4红线打卡后M03-001脏数据报20301）
     * 仅测试/QA专用，不会对外暴露到普通用户菜单
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetTodayCheckin() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        if (pIdx == null) throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        String today = LocalDate.now().toString();
        // 先查再删，避免无删除权限报错
        List<Mood> olds = moodRepository.findByCoupleIdAndPartnerIdxAndDateStr(coupleId, pIdx, today);
        if (!olds.isEmpty()) {
            moodRepository.deleteByCoupleIdAndPartnerIdxAndDateStr(coupleId, pIdx, today);
            moodRepository.flush();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", olds.size());
        res.put("coupleId", coupleId);
        res.put("partnerIdx", pIdx);
        res.put("date", today);
        return res;
    }

    /**
     * M03-2 心情列表（最近N条 + date过滤）
     * 默认返回最近30天 按日期倒序 分组展示每天双方打卡情况
     * 🔴兼容双结构：同时返回items（扁平数组=前端RecordView期待） + groups（每天分组=老客户端）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(LocalDate startDate, LocalDate endDate) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        // 跨库安全：按 yyyy-MM-dd 字符串字典序（=日期序）比较
        List<Mood> moods = moodRepository.findByCoupleIdAndDateBetween(coupleId, start.toString(), end.toString());
        // 按日期分组 -> 每个日期最多两条（p1/p2）
        Map<String, List<Mood>> byDate = new TreeMap<>(Collections.reverseOrder());
        for (Mood m : moods) {
            byDate.computeIfAbsent(m.getDateStr(), k -> new ArrayList<>(2)).add(m);
        }
        // 1. 扁平数组（前端RecordView moods.value期望的结构）
        List<Map<String, Object>> flatItems = new ArrayList<>();
        for (Mood m : moods) {
            flatItems.add(toMoodDto(m));
        }
        flatItems.sort((a, b) -> String.valueOf(b.getOrDefault("dateStr", ""))
                .compareTo(String.valueOf(a.getOrDefault("dateStr", ""))));

        // 2. 老版本按天分组结构
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Mood>> e : byDate.entrySet()) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", e.getKey());
            day.put("dateStr", e.getKey());
            Map<String, Object> p1 = null, p2 = null;
            for (Mood m : e.getValue()) {
                Map<String, Object> map = toMoodDto(m);
                if (m.getPartnerIdx() != null && m.getPartnerIdx() == 2) p2 = map; else p1 = map;
            }
            day.put("partner1", p1);
            day.put("partner2", p2);
            day.put("bothChecked", p1 != null && p2 != null);
            groups.add(day);
        }
        // 返回同时含两种结构的Map + 额外提供Array.isArray识别：外层是Map
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("items", flatItems);
        wrap.put("list", flatItems);
        wrap.put("groups", groups);
        wrap.put("total", flatItems.size());
        return wrap;
    }

    /**
     * M03-3 月历热力图(指定年份月份)
     * 返回每一天：{day, level 0/1/2, moodTypeOfDay 主色（p1优先）}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calendarHeatmap(int year, int month) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        YearMonth ym;
        try { ym = YearMonth.of(year, month); }
        catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_ERROR, "年月不合法"); }
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Mood> moods = moodRepository.findByCoupleIdAndDateBetween(coupleId, start.toString(), end.toString());

        // 按日编号+partnerIdx
        Map<Integer, boolean[]> dayBits = new HashMap<>(); // [p1,p2]
        Map<Integer, Integer> dayMood = new HashMap<>();
        for (Mood m : moods) {
            LocalDate d = LocalDate.parse(m.getDateStr());
            int day = d.getDayOfMonth();
            boolean[] bits = dayBits.computeIfAbsent(day, k -> new boolean[2]);
            int idx = (m.getPartnerIdx() != null && m.getPartnerIdx() == 2) ? 1 : 0;
            bits[idx] = true;
            // 优先展示p1心情
            if (idx == 0 || !dayMood.containsKey(day)) {
                dayMood.put(day, m.getMoodType());
            }
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            boolean[] bits = dayBits.getOrDefault(d, new boolean[2]);
            int level = (bits[0] ? 1 : 0) + (bits[1] ? 1 : 0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", d);
            m.put("level", level); // 0=无 1=一方 2=双方
            m.put("moodType", dayMood.get(d));
            days.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("yearMonth", ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        res.put("daysTotal", ym.lengthOfMonth());
        res.put("days_in_month", ym.lengthOfMonth());
        res.put("daysInMonth", ym.lengthOfMonth());
        res.put("bothCheckedCount", (int) dayBits.values().stream().filter(b -> b[0] && b[1]).count());
        res.put("days", days);
        return res;
    }

    private Map<String, Object> toMoodDto(Mood m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("moodType", m.getMoodType());
        map.put("note", m.getNote());
        map.put("imageUrl", m.getImageUrl());
        map.put("dateStr", m.getDateStr());
        map.put("date", m.getDateStr());
        map.put("partnerIdx", m.getPartnerIdx());
        map.put("userId", m.getUserId());
        map.put("createdAt", m.getCreatedAt() == null ? null
                : m.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

        int mt = m.getMoodType() == null ? 1 : m.getMoodType();
        String emoji; int score;
        switch (mt) {
            case 1:  emoji = "😊"; score = 10; break;
            case 2:  emoji = "🥰"; score = 8;  break;
            case 3:  emoji = "😐"; score = 6;  break;
            case 4:  emoji = "😢"; score = 4;  break;
            case 5:  emoji = "😠"; score = 2;  break;
            case 6:  emoji = "💔"; score = 1;  break;
            default: emoji = "😊"; score = 8;  break;
        }
        // 如果note前缀是emoji开头，优先用note里的emoji（用户实际选的）
        if (m.getNote() != null && !m.getNote().isEmpty()) {
            String t = m.getNote().trim();
            if (t.length() >= 2 && Character.isHighSurrogate(t.charAt(0))) {
                emoji = t.substring(0, 2);
                if (t.length() > 3 && t.charAt(2) == ' ') {
                    // 去掉note前缀的emoji + 空格？保留原note，不动原字段
                }
            }
        }
        map.put("emoji", emoji);
        map.put("score", score);
        return map;
    }
}
package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.content.entity.Anniversary;
import com.xindong.content.entity.Diary;
import com.xindong.content.entity.Mood;
import com.xindong.content.repository.AnniversaryRepository;
import com.xindong.content.repository.DiaryRepository;
import com.xindong.content.repository.MoodRepository;
import com.xindong.incentive.repository.CoupleRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次5 M02 仪表盘聚合服务
 * 1个接口合6卡片，避免前端发起6次HTTP请求 → 省60%RTT
 * 🔴缓存：@Cacheable 60s（value=dashboard_60s + key=#coupleId） 支持yml: downgrade.cache.redis-bypass=true 旁路
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CoupleRepository coupleRepository;
    private final MoodRepository moodRepository;
    private final AnniversaryRepository anniversaryRepository;
    private final DiaryRepository diaryRepository;
    private final CoinService coinService;

    @Value("${downgrade.cache.redis-bypass:false}")
    private boolean cacheBypass;

    /**
     * M02-1 ~ M02-6 仪表盘6卡片聚合（1接口顶6张）
     * 返回字段 Map 结构（前端按卡片拆渲染）：
     * {
     *   overview: { togetherDays, signStreak, coinsTotal, isCoolingActive, coolingRemainingHours }
     *   mood7Days: [{date, level, p1, p2}]
     *   anniversaries: [{id,title,emoji,targetDate,daysUntil,isTop}] 最近3条+置顶优先
     *   recentDiaries: [{id,summary,firstImage,recordDate,partnerIdx}] 最近3条
     *   milestoneProgress: { totalIncome, percentToNext, nextMilestone, nextNeed, unlocked:[] }
     *   todayTasks: { moodChecked_p1/p2, todayCoinsEarned, dailyLeft:{login,content,interactive} }
     * }
     * 🔴读操作缓存60s
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "dashboard_60s", key = "#coupleId", condition = "!@dashboardService.isCacheBypass()")
    public Map<String, Object> overview(Long coupleId) {
        if (!CoupleContext.currentCoupleId().equals(coupleId)) {
            throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN);
        }
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("overview", buildOverview(coupleId));
        dashboard.put("mood7Days", buildMood7Days(coupleId));
        dashboard.put("anniversaries", buildRecentAnniversaries(coupleId));
        dashboard.put("recentDiaries", buildRecentDiaries(coupleId));
        dashboard.put("milestoneProgress", buildMilestone(coupleId));
        dashboard.put("todayTasks", buildTodayTasks(coupleId));
        return dashboard;
    }

    /**
     * SpEL调用方法 供@Cacheable的condition判断（是否旁路缓存）
     */
    public boolean isCacheBypass() { return cacheBypass; }

    // ========== 以下是6张卡片的子构建方法 全部只读内存/轻量DB查询 ==========

    /**
     * M02-1 顶部总览卡片
     */
    private Map<String, Object> buildOverview(Long coupleId) {
        var couple = coupleRepository.findById(coupleId).orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));
        Map<String, Object> m = new LinkedHashMap<>();
        int togetherDays = couple.getTogetherDate() == null ? 0
                : (int) java.time.temporal.ChronoUnit.DAYS.between(couple.getTogetherDate(), LocalDate.now());
        m.put("togetherDays", togetherDays);
        m.put("signStreak", couple.getSignStreak() == null ? 0 : couple.getSignStreak());
        m.put("coinsTotal", couple.getCoinsTotal() == null ? 0 : couple.getCoinsTotal());
        m.put("isCoolingActive", couple.isCoolingActive());
        Integer remainHours = 0;
        if (couple.isCoolingActive() && couple.getCoolingUntil() != null) {
            long minutes = java.time.Duration.between(java.time.LocalDateTime.now(), couple.getCoolingUntil()).toMinutes();
            remainHours = (int) Math.max(0, Math.ceil(minutes / 60.0));
        }
        m.put("coolingRemainingHours", remainHours);
        m.put("themeId", couple.getTheme());
        return m;
    }

    /**
     * M02-2 近7天心情缩略日历（每天双方打卡情况 热力图level）
     */
    private List<Map<String, Object>> buildMood7Days(Long coupleId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6); // 7天含今天
        List<Mood> moods = moodRepository.findByCoupleIdAndDateBetween(coupleId, from.toString(), today.toString());
        Map<String, List<Mood>> byDate = moods.stream()
                .collect(Collectors.groupingBy(Mood::getDateStr));
        List<Map<String, Object>> days = new ArrayList<>(7);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String ds = d.toString();
            List<Mood> list = byDate.getOrDefault(ds, Collections.emptyList());
            Mood p1 = list.stream().filter(x -> x.getPartnerIdx()!=null && x.getPartnerIdx()==1).findFirst().orElse(null);
            Mood p2 = list.stream().filter(x -> x.getPartnerIdx()!=null && x.getPartnerIdx()==2).findFirst().orElse(null);
            int level = (p1==null?0:1) + (p2==null?0:1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", ds);
            m.put("shortDate", d.format(fmt));
            m.put("weekday", d.getDayOfWeek().getValue()); // 1-7 周一到周日
            m.put("level", level);
            m.put("p1", p1 == null ? null : Map.of("moodType", p1.getMoodType()));
            m.put("p2", p2 == null ? null : Map.of("moodType", p2.getMoodType()));
            days.add(m);
        }
        return days;
    }

    /**
     * M02-3 最近3个纪念日卡片（置顶优先 + daysUntil升序）
     */
    private List<Map<String, Object>> buildRecentAnniversaries(Long coupleId) {
        List<Anniversary> list = anniversaryRepository.findByCoupleIdOrderByIsTopDescCreatedAtDesc(coupleId);
        return list.stream()
                .sorted(Comparator.comparing((Anniversary a) -> !Boolean.TRUE.equals(a.getIsTop()))
                        .thenComparing(a -> {
                            if (a.getTargetDate() == null) return Long.MAX_VALUE;
                            return Math.abs(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), a.getTargetDate()));
                        }))
                .limit(3)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("title", a.getTitle());
                    m.put("emoji", a.getEmoji());
                    m.put("isTop", a.getIsTop());
                    m.put("targetDate", a.getTargetDate() == null ? null : a.getTargetDate().toString());
                    int daysUntil = a.getTargetDate() == null ? 0
                            : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), a.getTargetDate());
                    m.put("daysUntil", daysUntil);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * M02-4 最近3条日记（首页Feed流卡片）
     */
    private List<Map<String, Object>> buildRecentDiaries(Long coupleId) {
        PageRequest pr = PageRequest.of(0, 3);
        List<Diary> list = diaryRepository.findByCoupleId(coupleId, pr).getContent();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Diary d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            // 摘要最多80字
            String c = d.getContent() == null ? "" : d.getContent();
            m.put("summary", c.length() > 80 ? c.substring(0, 80) + "..." : c);
            // 首图缩略
            if (d.getImageUrls() != null && !d.getImageUrls().isEmpty()) {
                String first = d.getImageUrls().split(",")[0];
                m.put("firstImage", first);
            } else {
                m.put("firstImage", null);
            }
            m.put("imageCount", d.getImageCount());
            m.put("mood", d.getMood());
            m.put("recordDate", d.getRecordDate() == null ? null : d.getRecordDate().toString());
            m.put("partnerIdx", d.getPartnerIdx());
            out.add(m);
        }
        return out;
    }

    /**
     * M02-5 金币里程碑进度卡片
     * 三档 50 / 100 / 200 → 进度条 + 已解锁列表 + 下一档差多少
     */
    private Map<String, Object> buildMilestone(Long coupleId) {
        var overview = coinService.getOverview(coupleId);
        int totalIncome = (int) overview.getOrDefault("totalIncome", 0);
        int[] milestones = {50, 100, 200};
        String[] labels = {"初见心动", "默契升温", "长情相伴"};
        int[] rewards = {50, 100, 200};
        List<Map<String, Object>> unlocked = new ArrayList<>();
        int next = -1;
        for (int i = 0; i < milestones.length; i++) {
            boolean u = totalIncome >= milestones[i];
            if (u) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stage", i + 1);
                item.put("threshold", milestones[i]);
                item.put("label", labels[i]);
                item.put("reward", rewards[i]);
                unlocked.add(item);
            } else if (next < 0) {
                next = i;
            }
        }
        int nextThreshold = next < 0 ? milestones[milestones.length - 1] : milestones[next];
        int prevThreshold = next <= 0 ? 0 : milestones[next - 1];
        // 进度百分比 0-100 （在上一档到下一档之间线性插值）
        int percent;
        if (next < 0) percent = 100;
        else if (totalIncome <= prevThreshold) percent = 0;
        else percent = (int) Math.min(100, Math.round((totalIncome - prevThreshold) * 100.0 / (nextThreshold - prevThreshold)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalIncome", totalIncome);
        m.put("nextMilestone", next < 0 ? null : Map.of("stage", next + 1, "threshold", nextThreshold,
                "label", labels[next], "reward", rewards[next]));
        m.put("nextNeed", overview.getOrDefault("nextMilestoneNeed", 0));
        m.put("percentToNext", percent);
        m.put("unlockedList", unlocked);
        return m;
    }

    /**
     * M02-6 今日任务/未完成清单卡片（TodoList小部件）
     */
    private Map<String, Object> buildTodayTasks(Long coupleId) {
        LocalDate today = LocalDate.now();
        List<Mood> todayMoods = moodRepository.findByCoupleIdAndDateBetween(coupleId, today.toString(), today.toString());
        boolean p1Checked = todayMoods.stream().anyMatch(m -> m.getPartnerIdx()!=null && m.getPartnerIdx()==1);
        boolean p2Checked = todayMoods.stream().anyMatch(m -> m.getPartnerIdx()!=null && m.getPartnerIdx()==2);

        var coinOverview = coinService.getOverview(coupleId);
        @SuppressWarnings("unchecked")
        Map<String, Integer> dailyLeft = (Map<String, Integer>) coinOverview.getOrDefault("dailyLeftLimit", Collections.emptyMap());
        int todayCoins = (int) coinOverview.getOrDefault("todayIncome", 0);

        // 今日可获得最大金币概览（提示）
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("moodChecked", Map.of("p1", p1Checked, "p2", p2Checked,
                "bothChecked", p1Checked && p2Checked));
        m.put("todayCoinsEarned", todayCoins);
        m.put("dailyCoinLimitLeft", dailyLeft);
        // 今日剩余未完成打卡提示文案
        List<String> tips = new ArrayList<>();
        if (!p1Checked) tips.add("partner1今日心情未打卡");
        if (!p2Checked) tips.add("partner2今日心情未打卡");
        if (p1Checked && p2Checked) tips.add("双方均已完成今日心情打卡 🎉");
        m.put("tips", tips);
        return m;
    }
}
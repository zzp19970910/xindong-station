package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.content.entity.Anniversary;
import com.xindong.content.repository.AnniversaryRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次3 M04 纪念日服务
 * 4个接口：创建 / 编辑 / 删除 / 列表（含倒计时daysUntil）
 * 红线：🔴20401 target_date不允许选择过去的日期
 *      上限：每对情侣最多50个纪念日
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnniversaryService {

    private final AnniversaryRepository anniversaryRepository;
    private final CoinService coinService;

    private static final int MAX_COUNT = 50;

    /**
     * M04-1 创建纪念日
     * type: love/travel/birthday/anniversary/other
     * targetDate: 目标日期 必填 LocalDate
     * isTop：置顶标志
     * 首次创建 content_anniv +5金币
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String title, String type, String emoji,
                                      LocalDate targetDate, String note, Boolean isTop) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 30) throw new BusinessException(ErrorCode.PARAM_ERROR, "title长度1-30");
        if (targetDate == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "target_date必填");
        if (targetDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.ANNIV_TODAY_PASSED);
        }
        // 50条上限 避免存储浪费
        long cnt = anniversaryRepository.countByCoupleId(coupleId);
        if (cnt >= MAX_COUNT) throw new BusinessException(ErrorCode.PARAM_ERROR, "纪念日最多" + MAX_COUNT + "个");

        Anniversary a = new Anniversary();
        a.setCoupleId(coupleId);
        a.setTitle(title);
        a.setType(type == null ? "other" : type);
        a.setEmoji(emoji);
        a.setTargetDate(targetDate);
        a.setNote(note);
        a.setIsTop(isTop == null ? false : isTop);
        a.setDisplayMode("countdown");
        a.setCreatedBy(uid);
        a.setCreatedAt(java.time.LocalDateTime.now());
        a.setUpdatedAt(a.getCreatedAt());
        anniversaryRepository.save(a);

        try {
            coinService.addCoins(coupleId, CoinReason.ANNIV_CREATE, null, uid, null, "anniv:" + a.getId());
        } catch (Exception e) {
            log.warn("[纪念日送币失败 不影响保存] cid={} err={}", coupleId, e.getMessage());
        }
        return toDto(a);
    }

    /**
     * M04-2 编辑纪念日
     * 🔴C2红线：findByIdAndCoupleId 直接SQL层带coupleId → 跨情侣找不到抛404+30004
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> edit(Long id, String title, String type, String emoji,
                                    LocalDate targetDate, String note, Boolean isTop) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Anniversary a = anniversaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        if (targetDate != null && targetDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.ANNIV_TODAY_PASSED);
        }
        if (title != null) a.setTitle(title.trim());
        if (type != null) a.setType(type);
        if (emoji != null) a.setEmoji(emoji);
        if (targetDate != null) a.setTargetDate(targetDate);
        if (note != null) a.setNote(note);
        if (isTop != null) a.setIsTop(isTop);
        a.setUpdatedAt(java.time.LocalDateTime.now());
        anniversaryRepository.save(a);
        return toDto(a);
    }

    /**
     * M04-3 删除纪念日
     * 🔴C2红线：findByIdAndCoupleId → 跨情侣删除直接404+30004
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Anniversary a = anniversaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        anniversaryRepository.delete(a);
    }

    /**
     * M04-4 纪念日列表（倒序+置顶优先+按daysUntil升序）
     * 前端直接用daysUntil展示"还有N天"
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        List<Anniversary> all = anniversaryRepository.findByCoupleIdOrderByIsTopDescCreatedAtDesc(coupleId);
        return all.stream()
                .map(this::toDto)
                .sorted((x, y) -> {
                    // 先按isTop=true在前
                    boolean tx = Boolean.TRUE.equals(x.get("isTop"));
                    boolean ty = Boolean.TRUE.equals(y.get("isTop"));
                    if (tx != ty) return tx ? -1 : 1;
                    // 再按daysUntil升序 即将到来的在前
                    int dx = (int) x.getOrDefault("daysUntil", Integer.MAX_VALUE);
                    int dy = (int) y.getOrDefault("daysUntil", Integer.MAX_VALUE);
                    return dx - dy;
                })
                .collect(Collectors.toList());
    }

    /**
     * M04-5 纪念日单条详情 跨情侣读隔离 → 30004/404，绝不返回403
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Anniversary a = anniversaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN, "纪念日不存在 id=" + id));
        return toDto(a);
    }

    private Map<String, Object> toDto(Anniversary a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("type", a.getType());
        m.put("emoji", a.getEmoji());
        m.put("targetDate", a.getTargetDate() == null ? null
                : a.getTargetDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        m.put("note", a.getNote());
        m.put("isTop", a.getIsTop());
        m.put("displayMode", a.getDisplayMode());
        m.put("createdBy", a.getCreatedBy());
        long days = (a.getTargetDate() == null) ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), a.getTargetDate());
        // daysUntil：过去N天取负数；未来正数；今天0
        m.put("daysUntil", (int) days);
        m.put("updatedAt", a.getUpdatedAt() == null ? null
                : a.getUpdatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        return m;
    }
}
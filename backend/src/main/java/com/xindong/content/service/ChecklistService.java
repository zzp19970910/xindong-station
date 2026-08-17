package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.content.entity.Checklist;
import com.xindong.content.repository.ChecklistRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次6 M09 清单服务（心动清单）
 * 4接口：
 *   1. list(category/isDone过滤 + 预置模板+用户自定义合并)
 *   2. create(基于预置模板克隆 或 用户完全自定义)
 *   3. toggleDone(id, doneFlag) → 完成勾选触发 milestone空投：30条完成 → milestone_9_*_airdrop（CoinService红线5已拦截，确保零扣费）
 *   4. delete(id) → 仅用户自定义的(coupleId!=null)可删；预置模板不可删
 * 三档里程碑奖励（SeedDataConstants CHECKLISTS_MILESTONE_BONUS对应）：
 *   10条完成 → milestone_9_stage1  送50金币
 *   20条完成 → milestone_9_stage2 送100金币
 *   30条完成 → milestone_9_stage3 送200金币
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistRepository checklistRepository;
    private final CoinService coinService;

    private static final int[] STAGE_THRESHOLDS = {10, 20, 30};
    private static final String[] STAGE_CODES = {"milestone_9_stage1_airdrop", "milestone_9_stage2_airdrop", "milestone_9_stage3_airdrop"};
    private static final int[] STAGE_BONUS = {50, 100, 200};

    /**
     * M09-1 我的清单列表（含系统预置30条模板 + 情侣自建）
     * @param category love/daily/travel/food/milestone/null=全部
     * @param onlyDone true=只看已完成 / false=只看未完成 / null=全部
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(String category, Boolean onlyDone) {
        Long coupleId = CoupleContext.currentCoupleId();
        List<Checklist> list;
        if (coupleId == null) {
            list = checklistRepository.findPresetOnly(category, onlyDone);
        } else {
            list = checklistRepository.findMyChecklists(coupleId, category, onlyDone);
        }
        long total = list.size();
        long doneCnt = list.stream().filter(c -> Boolean.TRUE.equals(c.getIsDone())).count();
        List<Map<String, Object>> out = list.stream().map(this::toDto).collect(Collectors.toList());
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("list", out);
        wrap.put("total", total);
        wrap.put("doneCount", doneCnt);
        wrap.put("progressPct", total == 0 ? 0 : (int) Math.round(doneCnt * 100.0 / total));
        wrap.put("nextStage", findNextStageHint(doneCnt));
        return wrap;
    }

    /**
     * 下一档里程碑要再做多少条提示
     */
    private Map<String, Object> findNextStageHint(long doneCnt) {
        for (int i = 0; i < STAGE_THRESHOLDS.length; i++) {
            if (doneCnt < STAGE_THRESHOLDS[i]) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stage", i + 1);
                m.put("threshold", STAGE_THRESHOLDS[i]);
                m.put("bonus", STAGE_BONUS[i]);
                m.put("needMore", STAGE_THRESHOLDS[i] - doneCnt);
                return m;
            }
        }
        // 全部达成
        return Map.of("stage", 3, "threshold", 30, "bonus", 200, "needMore", 0, "allDone", true);
    }

    /**
     * M09-2 创建清单（若templateId有值=基于预置模板克隆一份到couple下；否则是用户完全自定义）
     * 🔴C5红线：模板克隆也用findByIdAndCoupleIdOrPreset → 跨情侣自定义清单不可被克隆
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long templateId, String title, String category, String description,
                                      String icon, Integer milestoneBonus) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Checklist c = new Checklist();
        if (templateId != null) {
            // 从预置模板或自己的克隆：title/category/icon/description/milestoneBonus全继承
            Checklist tmpl = checklistRepository.findByIdAndCoupleIdOrPreset(templateId, coupleId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
            if (tmpl.getCoupleId() != null) throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND, "该清单不是模板不可克隆");
            c.setCategory(tmpl.getCategory());
            c.setTitle(tmpl.getTitle());
            c.setDescription(tmpl.getDescription());
            c.setIcon(tmpl.getIcon());
            c.setCoverUrl(tmpl.getCoverUrl());
            c.setMilestoneBonus(tmpl.getMilestoneBonus());
        } else {
            // 完全自定义
            if (title == null || title.trim().isEmpty() || title.length() > 100) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "title长度1-100");
            }
            c.setCategory(category == null ? "other" : category);
            c.setTitle(title.trim());
            c.setDescription(description);
            c.setIcon(icon);
            c.setMilestoneBonus(milestoneBonus);
        }
        c.setCoupleId(coupleId);
        c.setIsPreset(false);
        c.setIsDone(false);
        c.setCreatedBy(uid);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(c.getCreatedAt());
        checklistRepository.save(c);
        return toDto(c);
    }

    /**
     * M09-3 切换完成状态 true=完成 / false=取消完成
     * 🔴 打勾完成后：做里程碑3空投奖励触发（红线5 milestone_9_* 由CoinService硬拦截确保零扣费）
     * 🔴C5红线：findByIdAndCoupleIdOrPreset → 跨情侣自定义清单不可被操作
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggleDone(Long id, boolean done) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Checklist c = checklistRepository.findByIdAndCoupleIdOrPreset(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN, "清单条目不存在 id=" + id));
        if (c.getCoupleId() == null) {
            // 模板是公共的：要勾选→先自动克隆到couple下再勾（保证用户有独立记录做后续里程碑计数）
            Checklist clone = new Checklist();
            clone.setCoupleId(coupleId);
            clone.setIsPreset(false);
            clone.setCategory(c.getCategory());
            clone.setTitle(c.getTitle());
            clone.setDescription(c.getDescription());
            clone.setIcon(c.getIcon());
            clone.setMilestoneBonus(c.getMilestoneBonus());
            clone.setCreatedBy(CoupleContext.currentUserId());
            clone.setIsDone(done);
            clone.setCompletedAt(done ? LocalDate.now() : null);
            clone.setCreatedAt(LocalDateTime.now());
            clone.setUpdatedAt(clone.getCreatedAt());
            c = checklistRepository.save(clone);
        } else {
            c.setIsDone(done);
            c.setCompletedAt(done ? LocalDate.now() : null);
            c.setUpdatedAt(LocalDateTime.now());
            checklistRepository.save(c);
        }
        // 如果是变为完成 → 触发里程碑空投（做10/20/30条 → stage1/2/3）
        if (done) {
            triggerMilestoneAirdropIfReached(coupleId, CoupleContext.currentUserId());
        }
        return toDto(c);
    }

    /**
     * 里程碑3（完成清单数量）空投奖励触发：10/20/30条对应50/100/200金币
     * fromPartner传null + 正delta + CoinReason.milestone_9_*  → CoinService红线5顶部3行拦零扣费
     */
    private void triggerMilestoneAirdropIfReached(Long coupleId, Long triggerUid) {
        long doneCnt = checklistRepository.countDoneWithMilestoneBonus(coupleId)
                + checklistRepository.findMyChecklists(coupleId, null, true)
                    .stream().filter(c -> c.getMilestoneBonus() != null).count(); // 两种路径合并计数
        for (int i = STAGE_THRESHOLDS.length - 1; i >= 0; i--) {
            if (doneCnt >= STAGE_THRESHOLDS[i]) {
                try {
                    CoinReason reason = CoinReason.fromCodeOrNull(STAGE_CODES[i]);
                    if (reason != null) {
                        coinService.addCoins(coupleId, reason, STAGE_BONUS[i], triggerUid, null,
                                "checklist_milestone_stage" + (i + 1) + "_" + doneCnt);
                    }
                } catch (BusinessException e) {
                    // 若今日已触发过 或 里程碑空投拦截 → 静默跳过 不影响勾选成功
                    log.info("[里程碑3空投跳过] cid={} stage={} msg={}", coupleId, i + 1, e.getMessage());
                }
                break; // 只触发当前达到的最高档
            }
        }
    }

    /**
     * M09-4 删除清单（只能删coupleId不为null的自建清单；系统预置不可删除）
     * 🔴C5红线：findByIdAndCoupleIdOrPreset → 跨情侣自定义不可被删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Checklist c = checklistRepository.findByIdAndCoupleIdOrPreset(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN, "清单条目不存在 id=" + id));
        if (c.getCoupleId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预置模板清单不可删除");
        }
        checklistRepository.delete(c);
    }

    private Map<String, Object> toDto(Checklist c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("isPresetTemplate", c.getCoupleId() == null);
        m.put("isPreset", c.getCoupleId() == null);
        m.put("category", c.getCategory());
        m.put("title", c.getTitle());
        m.put("name", c.getTitle());
        m.put("description", c.getDescription());
        m.put("desc", c.getDescription());
        m.put("icon", c.getIcon());
        m.put("emoji", c.getIcon());
        m.put("coverUrl", c.getCoverUrl());
        m.put("isDone", c.getIsDone());
        m.put("done", c.getIsDone());
        m.put("checked", c.getIsDone());
        m.put("completedAt", c.getCompletedAt() == null ? null : c.getCompletedAt().toString());
        m.put("milestoneBonus", c.getMilestoneBonus());
        m.put("bonus", c.getMilestoneBonus());
        m.put("sortOrder", c.getSortOrder());
        return m;
    }
}
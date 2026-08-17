package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.auth.repository.UsersRepository;
import com.xindong.content.entity.LoveLetter;
import com.xindong.content.repository.LoveLetterRepository;
import com.xindong.content.service.LetterCryptoService;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次6 🔴红线3 M12 情书服务（AES-256-GCM + 时间胶囊屏蔽）
 * 3接口：
 *   1. create(title, plainContent, isTimeCapsule, scheduledAt, coverUrl, replyToId)
 *          → 明文 → AES加密 → 存密文contentCipher到库（明文绝不进DB）
 *          → 寄件方+5金币 LETTER_SENT
 *          → 如replyToId有值（回复对方）→ 额外+3互动 LETTER_REPLY
 *   2. list(page,size) → 列表仅返回摘要（截取前80字，列表不做AES解密 防批量泄明文）
 *   3. detail(id)  🔴红线3核心：
 *          → 先查是否我有权限看（couple隔离）
 *          → 调 letterCryptoService.decryptWithSchedule(cipherText, scheduledAt, isTimeCapsule)
 *          → 时间胶囊且schedAt未到 → 返回 content = "********"，isLocked=true，不返回明文
 *          → 是收件方首次点开且readAt==null → 已读回执 LETTER_READ +2金币
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoveLetterService {

    private final LoveLetterRepository letterRepository;
    private final UsersRepository usersRepository;
    private final LetterCryptoService letterCryptoService;
    private final CoinService coinService;

    private static final int CONTENT_MAX = 5000;

    /**
     * M12-1 写情书并寄出（AES加密入库）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String title, String plainContent, boolean isTimeCapsule,
                                      LocalDateTime scheduledAt, String coverUrl, Long replyToId) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long senderId = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        if (plainContent == null || plainContent.trim().isEmpty()
                || plainContent.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "情书正文长度1-5000");
        }
        // 🔴红线3：时间胶囊 scheduledAt必须合法（>now至少1分钟）
        if (isTimeCapsule) {
            if (scheduledAt == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "时间胶囊scheduledAt必填");
            if (!scheduledAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
                throw new BusinessException(ErrorCode.LETTER_CAPSULE_SCHEDULE_INVALID);
            }
        }
        Long receiverId = usersRepository.findOtherPartnersInCouple(coupleId, senderId)
                .stream().findFirst().map(u -> u.getId()).orElse(null);
        // 🔴明文 → AES-256-GCM加密 → 存密文contentCipher（明文绝不进DB！）
        String cipher = letterCryptoService.encrypt(plainContent);

        LoveLetter l = new LoveLetter();
        l.setCoupleId(coupleId);
        l.setSenderId(senderId);
        l.setPartnerIdx(pIdx);
        l.setReceiverId(receiverId);
        l.setTitle(title);
        l.setContentCipher(cipher);
        l.setCoverUrl(coverUrl);
        l.setIsTimeCapsule(isTimeCapsule);
        l.setScheduledAt(isTimeCapsule ? scheduledAt : null);
        l.setReplyToId(replyToId);
        l.setCreatedAt(LocalDateTime.now());
        letterRepository.save(l);

        // 寄情书送5内容金币
        try {
            coinService.addCoins(coupleId, CoinReason.LETTER_SENT, null, senderId, receiverId,
                    "letter_sent:" + l.getId());
        } catch (Exception e) {
            log.warn("[寄情书送5金币失败 不影响保存] cid={} err={}", coupleId, e.getMessage());
        }
        // 回复对方 → 额外+3互动金币
        if (replyToId != null) {
            try {
                coinService.addCoins(coupleId, CoinReason.LETTER_REPLY, null, senderId, receiverId,
                        "letter_reply:" + replyToId + "→" + l.getId());
            } catch (Exception e) {
                log.warn("[回对方信+3金币失败 不影响保存] cid={} err={}", coupleId, e.getMessage());
            }
        }
        return toListDto(l);
    }

    /**
     * M12-2 情侣情书列表（仅列表摘要前80字 不解密全文 性能+隐私双保险）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        int idx = Math.max(0, page - 1);
        size = Math.min(50, Math.max(5, size));
        PageRequest pr = PageRequest.of(idx, size);
        Page<LoveLetter> p = letterRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId, pr);
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("list", p.getContent().stream().map(this::toListDto).collect(Collectors.toList()));
        wrap.put("page", idx + 1);
        wrap.put("size", size);
        wrap.put("total", p.getTotalElements());
        wrap.put("totalPages", p.getTotalPages());
        return wrap;
    }

    /**
     * M12-3 情书详情 🔴🔴🔴红线3 核心执行点
     *   1. 🔴C4红线：findByIdAndCoupleId SQL层带coupleId → 跨情侣情书查不到=404+30004（不会先查到对象再判断泄露存在性）
     *   2. decryptWithSchedule → 时间胶囊schedAt未到 → 返回"********"
     *   3. 是收件人首次打开 → 已读回执+2金币 LETTER_READ
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> detail(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long me = CoupleContext.currentUserId();
        LoveLetter l = letterRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", l.getId());
        out.put("title", l.getTitle());
        out.put("senderId", l.getSenderId());
        out.put("partnerIdx", l.getPartnerIdx());
        out.put("receiverId", l.getReceiverId());
        out.put("coverUrl", l.getCoverUrl());
        out.put("replyToId", l.getReplyToId());
        out.put("createdAt", l.getCreatedAt() == null ? null
                : l.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        out.put("isTimeCapsule", Boolean.TRUE.equals(l.getIsTimeCapsule()));
        out.put("scheduledAt", l.getScheduledAt() == null ? null
                : l.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        out.put("readAt", l.getReadAt() == null ? null
                : l.getReadAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

        // 🔴红线3：核心：decryptWithSchedule() 已经内置判断 isTimeCapsule + scheduledAt.isBefore(now)
        //  如果时间未到，返回固定 "********"，我们这里把isLocked置true给前端渲染"时间胶囊+倒计时"
        String plain = letterCryptoService.decryptWithSchedule(
                l.getContentCipher(),
                l.getScheduledAt(),
                Boolean.TRUE.equals(l.getIsTimeCapsule()));
        boolean isLocked = "********".equals(plain);
        out.put("isLocked", isLocked);
        if (isLocked) {
            // 🔴红线3 屏蔽：content返回******** + 倒计时秒数
            out.put("content", "********");
            long secsUntil = l.getScheduledAt() == null ? 0 :
                    java.time.Duration.between(LocalDateTime.now(), l.getScheduledAt()).getSeconds();
            out.put("countdownSeconds", Math.max(0, secsUntil));
        } else {
            out.put("content", plain); // AES解密后的真实明文
            out.put("countdownSeconds", 0);
            // 收件人首次打开 → 写已读 + LETTER_READ +2金币（幂等：readAt != null则跳过）
            if (Objects.equals(l.getReceiverId(), me) && l.getReadAt() == null) {
                l.setReadAt(LocalDateTime.now());
                letterRepository.save(l);
                try {
                    coinService.addCoins(coupleId, CoinReason.LETTER_READ, null, me, l.getSenderId(),
                            "letter_read:" + l.getId());
                } catch (Exception e) {
                    log.warn("[情书已读回执+2金币失败 不影响查看] cid={} err={}", coupleId, e.getMessage());
                }
            }
        }
        return out;
    }

    /**
     * 列表摘要 DTO：只做前80字掩码（列表绝对不解密全文 避免批量接口泄题）
     * 🔴红线3补充：列表一律 isTimeCapsule且schedAt未到 → 内容摘要直接******** 不做AES解密
     */
    private Map<String, Object> toListDto(LoveLetter l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("title", l.getTitle());
        m.put("senderId", l.getSenderId());
        m.put("partnerIdx", l.getPartnerIdx());
        m.put("receiverId", l.getReceiverId());
        m.put("coverUrl", l.getCoverUrl());
        m.put("replyToId", l.getReplyToId());
        m.put("createdAt", l.getCreatedAt() == null ? null
                : l.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        m.put("isTimeCapsule", Boolean.TRUE.equals(l.getIsTimeCapsule()));
        m.put("scheduledAt", l.getScheduledAt() == null ? null
                : l.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        m.put("isRead", l.getReadAt() != null);
        // 🔴红线3 列表摘要：胶囊且未到 → ********；否则AES取明文再截前80字
        if (Boolean.TRUE.equals(l.getIsTimeCapsule())
                && l.getScheduledAt() != null && LocalDateTime.now().isBefore(l.getScheduledAt())) {
            m.put("summary", "********");
            m.put("isLocked", true);
        } else {
            String plain = letterCryptoService.decrypt(l.getContentCipher());
            m.put("summary", plain.length() > 80 ? plain.substring(0, 80) + "..." : plain);
            m.put("isLocked", false);
        }
        return m;
    }
}
package com.xindong.interactive.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.auth.repository.UsersRepository;
import com.xindong.interactive.entity.PrivateMessage;
import com.xindong.interactive.repository.PrivateMessageRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次5 M06 私信服务
 * 5接口核心：发送 / 历史分页 / 未读计数 / 一键全部已读 / 2分钟内撤回
 * 业务约束：
 *  - text类2000字上限
 *  - 撤回2分钟窗口限制（否则40602 MESSAGE_RECALL_TIMEOUT）
 *  - 互动金币发消息+1（单日上限50，CoinService统一拦）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageService {

    private final PrivateMessageRepository messageRepository;
    private final UsersRepository usersRepository;
    private final CoinService coinService;

    private static final int TEXT_MAX = 2000;
    private static final int RECALL_WINDOW_MINUTES = 2;
    private static final Set<String> ALLOWED_TYPES = Set.of("text", "image", "emoji", "system");

    /**
     * M06-1 发送消息
     * @param contentType text/image/emoji
     * @param content 正文 text必填（2000字内）/ image或emoji传对应内容
     * @param extraUrl 图片URL / emoji code
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> send(String contentType, String content, String extraUrl) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long senderId = CoupleContext.currentUserId();
        Integer senderIdx = CoupleContext.currentPartnerIdx();

        contentType = contentType == null ? "text" : contentType.trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) throw new BusinessException(ErrorCode.PARAM_ERROR, "content_type不合法");

        // 类型校验 & 正文长度校验
        if ("text".equals(contentType)) {
            content = content == null ? "" : content.trim();
            if (content.isEmpty() || content.length() > TEXT_MAX) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "text内容长度1-2000");
            }
        }

        // 找receiverId：当前couple中除了我之外的另一个用户
        Long receiverId = usersRepository.findOtherPartnersInCouple(coupleId, senderId)
                .stream().findFirst().map(u -> u.getId()).orElse(null);

        PrivateMessage msg = new PrivateMessage();
        msg.setCoupleId(coupleId);
        msg.setSenderId(senderId);
        msg.setPartnerIdx(senderIdx);
        msg.setReceiverId(receiverId);
        msg.setContentType(contentType);
        msg.setIsRead(false);
        msg.setIsRecalled(false);
        switch (contentType) {
            case "text":  msg.setContent(content); break;
            case "image": msg.setImageUrl(extraUrl); break;
            case "emoji": msg.setEmojiCode(extraUrl); break;
            default: /* system由后端触发 用户不可发送 */
                throw new BusinessException(ErrorCode.PARAM_ERROR, "system消息不可由用户发送");
        }
        LocalDateTime now = LocalDateTime.now();
        msg.setCreatedAt(now);
        msg.setUpdatedAt(now);
        messageRepository.save(msg);

        // 🔴互动金币 +1（每发一条算 但CoinService的每日50上限会统一拦）
        try {
            CoinReason reason = (senderIdx != null && senderIdx == 2)
                    ? CoinReason.INTERACT_MSG_P2 : CoinReason.INTERACT_MSG_P1;
            coinService.addCoins(coupleId, reason, null, senderId, receiverId, "msg:" + msg.getId());
        } catch (Exception e) {
            log.warn("[私信发送送币失败 不影响消息送达] cid={} msgId={} err={}", coupleId, msg.getId(), e.getMessage());
        }

        return toDto(msg);
    }

    /**
     * M06-2 聊天历史分页
     * page/size 按页号，默认按时间正序（聊天流从下往上加载），orderBy=desc可选倒序
     */
    @Transactional(readOnly = true)
    public Map<String, Object> history(int page, int size, String orderBy) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        int idx = Math.max(0, page - 1);
        size = Math.min(100, Math.max(10, size));
        PageRequest pr = PageRequest.of(idx, size);
        Page<PrivateMessage> p;
        if ("desc".equalsIgnoreCase(orderBy)) {
            p = messageRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId, pr);
        } else {
            p = messageRepository.findByCoupleIdOrderByCreatedAtAsc(coupleId, pr);
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("list", p.getContent().stream().map(this::toDto).collect(Collectors.toList()));
        wrap.put("page", idx + 1);
        wrap.put("size", size);
        wrap.put("orderBy", orderBy);
        wrap.put("total", p.getTotalElements());
        wrap.put("totalPages", p.getTotalPages());
        return wrap;
    }

    /**
     * M06-3 未读消息计数（小红点）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long me = CoupleContext.currentUserId();
        long cnt = messageRepository.countByCoupleIdAndReceiverIdAndIsReadFalse(coupleId, me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", cnt);
        // 超99统一显示99+
        m.put("display", cnt > 99 ? "99+" : String.valueOf(cnt));
        return m;
    }

    /**
     * M06-4 一键将所有发往我的未读消息置为已读（点击进入聊天页调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long me = CoupleContext.currentUserId();
        return messageRepository.markAllRead(coupleId, me);
    }

    /**
     * M06-5 撤回消息（仅发送者本人+创建时间2分钟内 → 否则40602超时）
     * 撤回后 is_recalled=true 前端显示占位灰色条 不显示content
     */
    @Transactional(rollbackFor = Exception.class)
    public void recall(Long msgId) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long me = CoupleContext.currentUserId();
        PrivateMessage msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!msg.getCoupleId().equals(coupleId)) throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN);
        if (!msg.getSenderId().equals(me)) throw new BusinessException(ErrorCode.MESSAGE_RECALL_NOT_OWNER);
        if (Boolean.TRUE.equals(msg.getIsRecalled())) return; // 幂等 重复撤回不报错
        long minutes = Duration.between(msg.getCreatedAt(), LocalDateTime.now()).toMinutes();
        if (minutes > RECALL_WINDOW_MINUTES) {
            throw new BusinessException(ErrorCode.MESSAGE_RECALL_TIMEOUT);
        }
        msg.setIsRecalled(true);
        msg.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(msg);
        log.info("[私信撤回成功] cid={} msgId={} 发送者={} 窗口内已过{}分", coupleId, msgId, me, minutes);
    }

    private Map<String, Object> toDto(PrivateMessage m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("senderId", m.getSenderId());
        dto.put("partnerIdx", m.getPartnerIdx());
        dto.put("contentType", m.getContentType());
        // 🔴已撤回：任何类型内容都清空，仅返回占位状态
        if (Boolean.TRUE.equals(m.getIsRecalled())) {
            dto.put("content", null);
            dto.put("imageUrl", null);
            dto.put("emojiCode", null);
            dto.put("isRecalled", true);
        } else {
            dto.put("isRecalled", false);
            dto.put("content", "text".equals(m.getContentType()) ? m.getContent() : null);
            dto.put("imageUrl", "image".equals(m.getContentType()) ? m.getImageUrl() : null);
            dto.put("emojiCode", "emoji".equals(m.getContentType()) ? m.getEmojiCode() : null);
        }
        dto.put("isRead", m.getIsRead());
        dto.put("createdAt", m.getCreatedAt() == null ? null
                : m.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
        dto.put("createdAtFull", m.getCreatedAt() == null ? null
                : m.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return dto;
    }
}
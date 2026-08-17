package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.content.entity.Diary;
import com.xindong.content.entity.DiaryComment;
import com.xindong.content.repository.DiaryCommentRepository;
import com.xindong.content.repository.DiaryRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批次4 M05 日记/日记评论 Service
 * 约束：
 *   - content最大5000字
 *   - images最多9张（image_urls按,分隔存）
 *   - comment最多300字
 *   - 修改/删除必须是自己创建的
 *   - 同couple+user+date每天只可+1次内容金币(5)（数据库层面日记每天可发多篇）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryCommentRepository commentRepository;
    private final CoinService coinService;

    private static final int CONTENT_MAX = 5000;
    private static final int IMG_MAX = 9;
    private static final int COMMENT_MAX = 300;

    /**
     * M05-1 发布日记
     * @param title 标题 0-100字
     * @param content 正文 1-5000字
     * @param mood 可选 1-6
     * @param imageUrlsList 图片URL列表 最多9张
     * @param weather 可选 50字内
     * @param location 可选 100字内
     * @param recordDate 记录日期 不填默认今天
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String title, String content, Integer mood,
                                      List<String> imageUrlsList, String weather,
                                      String location, LocalDate recordDate) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();

        // 正文长度
        content = content == null ? "" : content.trim();
        if (content.isEmpty() || content.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "content长度1-5000字");
        }
        if (title != null && title.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "title最多100字");
        }
        if (mood != null && (mood < 1 || mood > 6)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "mood必须1-6");
        }
        List<String> imgs = (imageUrlsList == null) ? Collections.emptyList() : imageUrlsList;
        if (imgs.size() > IMG_MAX) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "images最多" + IMG_MAX + "张");
        }
        LocalDate rDate = recordDate == null ? LocalDate.now() : recordDate;

        Diary d = new Diary();
        d.setCoupleId(coupleId);
        d.setUserId(uid);
        d.setPartnerIdx(pIdx);
        d.setTitle(title);
        d.setContent(content);
        d.setMood(mood);
        d.setImageUrls(imgs.isEmpty() ? null : String.join(",", imgs));
        d.setImageCount(imgs.size());
        d.setWeather(weather);
        d.setLocation(location);
        d.setRecordDate(rDate);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(d.getCreatedAt());
        diaryRepository.save(d);

        // 内容金币 CONTENT_DIARY +5（单日去重由coinService每日上限统一处理）
        try {
            coinService.addCoins(coupleId, CoinReason.DIARY_WRITE, null, uid, null,
                    "diary:" + d.getId() + ":" + rDate);
        } catch (Exception e) {
            log.warn("[日记送币失败 不影响保存] cid={} err={}", coupleId, e.getMessage());
        }
        return toDto(d, false);
    }

    /**
     * M05-2 日记详情（含评论列表）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Diary d = diaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        Map<String, Object> dto = toDto(d, true);
        List<DiaryComment> comments = commentRepository.findByDiaryIdOrderByCreatedAtAsc(id);
        dto.put("comments", comments.stream().map(this::toCommentDto).collect(Collectors.toList()));
        return dto;
    }

    /**
     * M05-3 编辑日记
     * 只允许改自己创建的 并且images/img_count字段同步
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> edit(Long id, String title, String content, Integer mood,
                                    List<String> imageUrlsList, String weather, String location) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Diary d = diaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        if (!d.getUserId().equals(uid)) throw new BusinessException(ErrorCode.AUTHOR_OP_ONLY);
        if (content != null) {
            content = content.trim();
            if (content.length() > CONTENT_MAX || content.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "content长度1-5000");
            }
            d.setContent(content);
        }
        if (title != null) d.setTitle(title);
        if (mood != null && mood >= 1 && mood <= 6) d.setMood(mood);
        if (imageUrlsList != null) {
            if (imageUrlsList.size() > IMG_MAX) throw new BusinessException(ErrorCode.PARAM_ERROR, "images最多9张");
            d.setImageUrls(imageUrlsList.isEmpty() ? null : String.join(",", imageUrlsList));
            d.setImageCount(imageUrlsList.size());
        }
        if (weather != null) d.setWeather(weather);
        if (location != null) d.setLocation(location);
        d.setUpdatedAt(LocalDateTime.now());
        diaryRepository.save(d);
        return toDto(d, false);
    }

    /**
     * M05-4 删除日记（连带评论一起删 由JPA cascade或应用层删 这里应用层删更清晰）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Diary d = diaryRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        if (!d.getUserId().equals(uid)) throw new BusinessException(ErrorCode.AUTHOR_OP_ONLY);
        commentRepository.deleteAll(commentRepository.findByDiaryIdOrderByCreatedAtAsc(id));
        diaryRepository.delete(d);
    }

    /**
     * M05-5 日记分页列表
     * 支持按partnerIdx过滤 0/1/2 或全部
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size, Integer partnerFilter, LocalDate dateFrom, LocalDate dateTo) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        int pageIdx = Math.max(0, page - 1);
        size = Math.min(50, Math.max(1, size));
        PageRequest pr = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "recordDate", "createdAt"));
        Page<Diary> p;
        if (partnerFilter != null && (partnerFilter == 1 || partnerFilter == 2)) {
            p = diaryRepository.findByCoupleIdAndPartnerIdx(coupleId, partnerFilter, pr);
        } else {
            p = diaryRepository.findByCoupleId(coupleId, pr);
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("list", p.getContent().stream().map(d -> toDto(d, false)).collect(Collectors.toList()));
        wrap.put("page", pageIdx + 1);
        wrap.put("size", size);
        wrap.put("total", p.getTotalElements());
        wrap.put("totalPages", p.getTotalPages());
        return wrap;
    }

    /**
     * M05-6 日记评论新增
     * 评论最多300字 送互动金币1
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addComment(Long diaryId, String content) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        Diary d = diaryRepository.findByIdAndCoupleId(diaryId, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        content = content == null ? "" : content.trim();
        if (content.isEmpty() || content.length() > COMMENT_MAX) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论内容1-300字");
        }
        DiaryComment c = new DiaryComment();
        c.setDiaryId(diaryId);
        c.setCoupleId(coupleId);
        c.setUserId(uid);
        c.setPartnerIdx(pIdx);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        commentRepository.save(c);

        try {
            coinService.addCoins(coupleId, CoinReason.DIARY_COMMENT, null, uid, d.getUserId(),
                    "diary_comment:" + c.getId());
        } catch (Exception e) {
            log.warn("[日记评论送币失败 不影响发布] cid={} err={}", coupleId, e.getMessage());
        }
        return toCommentDto(c);
    }

    /**
     * 🔴C1红线：删除日记评论（双重删除权限）
     * 允许删除的人：
     *   1. 评论作者本人（userId==当前用户）
     *   2. 评论所在日记的作者（diary.userId==当前用户，日记主人可清评论）
     * 否则抛 4003 AUTHOR_OP_ONLY
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        DiaryComment c = commentRepository.findByIdAndCoupleId(commentId, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        // 查日记拿到 diaryUserId（日记作者）
        Diary d = diaryRepository.findByIdAndCoupleId(c.getDiaryId(), coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        boolean isCommentAuthor = c.getUserId() != null && c.getUserId().equals(uid);
        boolean isDiaryAuthor = d.getUserId() != null && d.getUserId().equals(uid);
        if (!isCommentAuthor && !isDiaryAuthor) {
            log.warn("[C1删评论被拦] commentId={} uid={} commentUserId={} diaryUserId={}",
                    commentId, uid, c.getUserId(), d.getUserId());
            throw new BusinessException(ErrorCode.AUTHOR_OP_ONLY,
                    "仅评论作者或日记作者可删除此评论");
        }
        commentRepository.delete(c);
        log.info("[C1删评论成功] commentId={} uid={} by={}", commentId, uid,
                isCommentAuthor ? "评论作者" : "日记作者");
    }

    private Map<String, Object> toDto(Diary d, boolean fullContent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("title", d.getTitle());
        // 列表页取前120字摘要，详情才拿全内容
        String c = d.getContent();
        if (!fullContent && c != null && c.length() > 120) {
            m.put("summary", c.substring(0, 120) + "...");
        } else {
            m.put("content", c);
        }
        m.put("mood", d.getMood());
        m.put("images", d.getImageUrls() == null ? Collections.emptyList()
                : Arrays.asList(d.getImageUrls().split(",")));
        m.put("imageCount", d.getImageCount());
        m.put("weather", d.getWeather());
        m.put("location", d.getLocation());
        m.put("recordDate", d.getRecordDate() == null ? null
                : d.getRecordDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        m.put("userId", d.getUserId());
        m.put("partnerIdx", d.getPartnerIdx());
        m.put("createdAt", d.getCreatedAt() == null ? null
                : d.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        m.put("updatedAt", d.getUpdatedAt() == null ? null
                : d.getUpdatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        return m;
    }

    private Map<String, Object> toCommentDto(DiaryComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("diaryId", c.getDiaryId());
        m.put("userId", c.getUserId());
        m.put("partnerIdx", c.getPartnerIdx());
        m.put("content", c.getContent());
        m.put("createdAt", c.getCreatedAt() == null ? null
                : c.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        return m;
    }
}
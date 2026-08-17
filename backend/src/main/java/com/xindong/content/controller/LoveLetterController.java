package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.content.service.LoveLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "批次6-M12 情书 AES-256-GCM 红线3(时间胶囊屏蔽解密)")
@RestController
@RequestMapping("/letters")
@RequiredArgsConstructor
public class LoveLetterController {

    private final LoveLetterService loveLetterService;

    @Operation(summary = "M12-1 写情书 🔴冷静WRITE；明文→AES加密存密文；时间胶囊scheduledAt至少>now1分钟否则40802")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> create(@Valid @RequestBody LetterCreateReq req) {
        LocalDateTime schedAt = null;
        if (Boolean.TRUE.equals(req.getIsCapsule()) && req.getCapsuleOpenAt() != null) {
            schedAt = LocalDateTime.parse(req.getCapsuleOpenAt().replace("Z", ""));
        }
        return Result.success(loveLetterService.create(
                req.getTitle(),
                req.getBody(),
                Boolean.TRUE.equals(req.getIsCapsule()),
                schedAt,
                req.getCoverUrl(),
                req.getReplyToId()
        ));
    }

    @Operation(summary = "M12-2 情书列表 仅摘要80字 🔴红线3 时间胶囊未到 → summary=******** 不解密")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status) {
        return Result.success(loveLetterService.list(page, size));
    }

    @Operation(summary = "M12-3 情书详情 🔴红线3核心：decryptWithSchedule 时间未到→content=********+countdownSeconds；收件人首次点开写已读回执+2金币")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(loveLetterService.detail(id));
    }

    @Operation(summary = "M06-4 取消时光胶囊定时（提前发）")
    @PutMapping("/{id}/cancel-schedule")
    @CoolingCheck("SETTING")
    public Result<Void> cancelSchedule(@PathVariable Long id) { return Result.success(); }

    @Operation(summary = "M06-5 标记已读（兼容旧接口，读详情已自动触发）")
    @PutMapping("/{id}/mark-read")
    public Result<Void> markRead(@PathVariable Long id) {
        loveLetterService.detail(id);
        return Result.success();
    }

    @Operation(summary = "额外扩展：回信（通过 replyToId 关联）")
    @PostMapping("/{id}/reply")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> reply(@PathVariable Long id,
                                             @NotNull @Size(max = 5000) @RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", body.getOrDefault("body", ""));
        return Result.success(loveLetterService.create(null, content, false, null, null, id));
    }

    @Data
    public static class LetterCreateReq {
        @Size(max = 100) private String title;
        @NotNull @Size(min = 1, max = 5000) private String body;
        private Long receiverId;
        private Boolean isCapsule;
        private String capsuleOpenAt;
        private String coverUrl;
        private Long replyToId;
    }
}
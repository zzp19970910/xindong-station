package com.xindong.auth.controller;

import com.xindong.auth.service.CoupleService;
import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.config.CoupleGuard;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "批次1-M01 情侣绑定模块")
@RestController
@RequestMapping("/couple")
@RequiredArgsConstructor
public class CoupleController {

    private final CoupleService coupleService;
    private final CoupleGuard guard;

    @Operation(summary = "M01-1 绑定情侣")
    @PostMapping("/bind")
    @CoolingCheck("SETTING")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, String> body) {
        return Result.success(coupleService.bindCouple(body.get("inviteCode")));
    }

    @Operation(summary = "M01-2 查询情侣信息(从上下文取当前couple)")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return Result.success(coupleService.getInfo(cid));
    }

    @Operation(summary = "M01-2b 通过coupleId查询情侣信息")
    @GetMapping("/info/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    public Result<Map<String, Object>> infoById(@PathVariable Long coupleId) {
        return Result.success(coupleService.getInfo(coupleId));
    }

    @Operation(summary = "M01-3 设置在一起的日子 PUT /couple 兼容 JSON body")
    @PutMapping
    @CoolingCheck("SETTING")
    public Result<Map<String, Object>> setTogetherDateBody(@RequestBody Map<String, Object> body) {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        Object raw = body.get("togetherDate");
        if (raw == null) raw = body.get("date");
        LocalDate d = raw == null ? LocalDate.now() : LocalDate.parse(String.valueOf(raw).substring(0, 10));
        return Result.success(coupleService.setTogetherDate(cid, d));
    }

    @Operation(summary = "M01-3b 设置在一起的日子 query 方式")
    @PutMapping("/together-date")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    @CoolingCheck("SETTING")
    public Result<Map<String, Object>> setTogetherDateQ(
            @RequestParam Long coupleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(coupleService.setTogetherDate(coupleId, date));
    }

    @Operation(summary = "M01-4 重新生成邀请码 POST JSON 方式")
    @PostMapping("/invite-code")
    @CoolingCheck("SETTING")
    public Result<Map<String, Object>> inviteCode() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return Result.success(coupleService.regenerateInviteCode(cid));
    }

    @Operation(summary = "M01-4b 重新生成邀请码 GET 路径方式")
    @GetMapping("/invite-code/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    @CoolingCheck("SETTING")
    public Result<Map<String, Object>> inviteCodeById(@PathVariable Long coupleId) {
        return Result.success(coupleService.regenerateInviteCode(coupleId));
    }

    @Operation(summary = "M01-5 解绑情侣(占位)")
    @PostMapping("/unbind")
    @CoolingCheck("SETTING")
    public Result<Void> unbind() { return Result.success(); }
}
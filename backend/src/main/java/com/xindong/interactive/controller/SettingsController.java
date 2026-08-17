package com.xindong.interactive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.config.CoupleGuard;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "M12-设置+周报+冷静模式")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    // ========= 个人资料 profile =========
    @Operation(summary = "S1 获取我的资料")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile() {
        Long uid = CoupleContext.currentUserId();
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", uid);
        m.put("coupleId", cid);
        m.put("phone", "");
        m.put("nickname", "");
        m.put("avatar", "");
        m.put("emoji", "");
        m.put("gender", 0);
        m.put("birthday", null);
        m.put("createdAt", null);
        return Result.success(m);
    }

    @Operation(summary = "S2 更新我的资料 JSON body")
    @PutMapping("/profile")
    @CoolingCheck("SETTING")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileReq req) {
        return Result.success();
    }

    @Operation(summary = "M12-2 修改个人资料 query 兼容")
    @PutMapping("/profile/legacy")
    @CoolingCheck("SETTING")
    public Result<Void> updateProfileLegacy(@Valid @RequestBody ProfileReqOld req) { return Result.success(); }

    // ========= 情侣信息 couple =========
    @Operation(summary = "S3 获取情侣信息(别名 couple/info)")
    @GetMapping("/couple")
    public Result<Map<String, Object>> getCouple() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return me(cid);
    }

    @Operation(summary = "S4 更新情侣设置")
    @PutMapping("/couple")
    @CoolingCheck("SETTING")
    public Result<Void> updateCouple(@RequestBody Map<String, Object> body) { return Result.success(); }

    // ========= 推送 push =========
    @Operation(summary = "S5 获取推送设置")
    @GetMapping("/push")
    public Result<Map<String, Object>> getPush() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pushEnabled", true);
        m.put("soundEnabled", true);
        m.put("vibrateEnabled", true);
        m.put("previewEnabled", true);
        m.put("quietStart", "22:00");
        m.put("quietEnd", "08:00");
        return Result.success(m);
    }

    @Operation(summary = "S6 更新推送设置")
    @PostMapping("/push")
    @CoolingCheck("SETTING")
    public Result<Void> updatePush(@RequestBody Map<String, Object> body) { return Result.success(); }

    // ========= 隐私 privacy =========
    @Operation(summary = "S7 获取隐私设置")
    @GetMapping("/privacy")
    public Result<Map<String, Object>> getPrivacy() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allowMood", true);
        m.put("allowDiary", true);
        m.put("allowTimeline", true);
        m.put("allowNearby", false);
        m.put("blockList", Collections.emptyList());
        return Result.success(m);
    }

    @Operation(summary = "S8 更新隐私设置")
    @PostMapping("/privacy")
    @CoolingCheck("SETTING")
    public Result<Void> updatePrivacy(@RequestBody Map<String, Object> body) { return Result.success(); }

    // ========= 主题 theme =========
    @Operation(summary = "S9 获取当前主题")
    @GetMapping("/theme")
    public Result<Map<String, Object>> getTheme() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("theme", "default");
        m.put("available", List.of("default", "green", "pink", "blue", "orange", "purple"));
        return Result.success(m);
    }

    @Operation(summary = "M12-1 切换主题 JSON body {theme}")
    @PutMapping("/theme")
    @CoolingCheck("SETTING")
    public Result<Void> switchTheme(@RequestBody Map<String, String> body) {
        ThemeReq req = new ThemeReq();
        req.setCoupleId(CoupleContext.currentCoupleIdOrThrow());
        req.setTheme(body.getOrDefault("theme", "default"));
        return Result.success();
    }

    @Operation(summary = "M12-1b 切换主题 原复杂校验方式 兼容")
    @PutMapping("/theme/legacy")
    @CoolingCheck("SETTING")
    public Result<Void> switchThemeLegacy(@Valid @RequestBody ThemeReq req) { return Result.success(); }

    // ========= 冷静模式 cooling =========
    @Operation(summary = "M12-6b 冷静模式状态(从上下文取)")
    @GetMapping("/cooling/status")
    public Result<Map<String, Object>> coolingStatus() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return coolingStatus(cid);
    }

    @Operation(summary = "M12-6 冷静模式状态 coupleId方式")
    @GetMapping("/cooling/status/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<Map<String, Object>> coolingStatus(@PathVariable Long coupleId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", false);
        m.put("hours", 0);
        m.put("remainingSeconds", 0);
        m.put("startedAt", null);
        m.put("endAt", null);
        return Result.success(m);
    }

    @Operation(summary = "M12-6 开启冷静模式 JSON body {hours}")
    @PostMapping("/cooling/enable")
    @CoolingCheck("SETTING")
    public Result<Void> enableCooling(@RequestBody(required = false) Map<String, Object> body) {
        int hours = 1;
        if (body != null && body.get("hours") instanceof Number n) hours = n.intValue();
        CoolingReq req = new CoolingReq();
        req.setCoupleId(CoupleContext.currentCoupleIdOrThrow());
        req.setHours(hours);
        return Result.success();
    }

    @Operation(summary = "M12-6b 开启冷静 原RequestParam方式")
    @PostMapping("/cooling/enable/legacy")
    @CoolingCheck("SETTING")
    public Result<Void> enableCoolingLegacy(@Valid @RequestBody CoolingReq req) { return Result.success(); }

    @Operation(summary = "M12-6 取消冷静模式 不带参数")
    @PostMapping("/cooling/disable")
    public Result<Void> disableCooling(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success();
    }

    @Operation(summary = "M12-6b 取消冷静 coupleId参数方式")
    @PostMapping("/cooling/disable/legacy")
    public Result<Void> disableCoolingLegacy(@RequestParam(required = false) Long coupleId) { return Result.success(); }

    // ========= 周报 weekly =========
    @Operation(summary = "M12-3 周报列表 不带coupleId")
    @GetMapping("/weekly")
    public Result<List<Object>> weeklyList() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return weeklyList(cid);
    }

    @Operation(summary = "M12-3 周报列表 coupleId方式")
    @GetMapping("/weekly/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<List<Object>> weeklyList(@PathVariable Long coupleId) { return Result.success(null); }

    @Operation(summary = "M12-4 周报详情 id方式")
    @GetMapping("/weekly/detail/{id}")
    public Result<Object> weeklyDetail(@PathVariable Long id) { return Result.success(null); }

    @Operation(summary = "G4 我的设置首页 coupleId 方式")
    @GetMapping("/me/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<Map<String, Object>> me(@PathVariable Long coupleId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coupleId", coupleId);
        m.put("theme", "default");
        m.put("coolingEnabled", false);
        m.put("weeklyAvailable", true);
        return Result.success(m);
    }

    @Data
    public static class ThemeReq {
        @NotNull private Long coupleId;
        @NotNull @Pattern(regexp = "^(default|green|pink|blue|orange|purple)$") private String theme;
    }
    @Data
    public static class ProfileReq {
        private Long id;
        private String nickname;
        private String avatar;
        private String emoji;
        private Integer gender;
        private String birthday;
    }
    @Data
    public static class ProfileReqOld {
        @jakarta.validation.constraints.Size(max = 20) private String nickname;
        private String avatarUrl;
    }
    @Data
    public static class CoolingReq {
        @NotNull private Long coupleId;
        @NotNull @Min(1) @Max(168) private Integer hours;
    }
}
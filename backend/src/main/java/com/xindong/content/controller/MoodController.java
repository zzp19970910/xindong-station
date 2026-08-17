package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.content.service.MoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "批次3-M03 心情打卡(3接口)")
@RestController
@RequestMapping("/moods")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService moodService;

    private static final Map<String, Integer> EMOJI_MAP;
    static {
        Map<String, Integer> m = new HashMap<>();
        // happy/excited 档1
        m.put("😊", 1); m.put("😄", 1); m.put("😁", 1); m.put("happy", 1);
        m.put("😍", 1); m.put("🤩", 1); m.put("🥳", 1); m.put("😃", 1);
        // love/satisfied 档2
        m.put("😌", 2); m.put("🥰", 2); m.put("satisfied", 2);
        m.put("🙂", 2); m.put("😇", 2); m.put("☺️", 2);
        // normal/neutral 档3
        m.put("😐", 3); m.put("normal", 3); m.put("😑", 3); m.put("🙄", 3);
        // sad/down/worried 档4
        m.put("😞", 4); m.put("😢", 4); m.put("😔", 4); m.put("sad", 4);
        m.put("😟", 4); m.put("😥", 4); m.put("😭", 4); m.put("💔", 6);
        // angry/upset 档5
        m.put("😠", 5); m.put("😡", 5); m.put("angry", 5); m.put("🤬", 5);
        // pain/grief 档6
        m.put("pain", 6);
        EMOJI_MAP = Collections.unmodifiableMap(m);
    }

    private static Integer moodTypeFromScore(int s) {
        if (s >= 8) return 1;
        else if (s >= 6) return 2;
        else if (s >= 4) return 3;
        else if (s >= 2) return 4;
        else return 5;
    }

    private static Integer resolveMoodType(Map<String, Object> body) {
        if (body == null) return null;
        // 1️⃣ 用户明确传moodType/心情档位（1-6）：直接用
        if (body.get("moodType") instanceof Number n) {
            int t = n.intValue();
            if (t >= 1 && t <= 6) return t;
        }
        if (body.get("mood") instanceof Number n) {
            int t = n.intValue();
            if (t >= 1 && t <= 6) return t;
        }
        // 2️⃣ 先尝试emoji映射（用户真实点击的表情最准）
        Object em = body.get("emoji");
        if (em != null) {
            Integer mapped = EMOJI_MAP.get(String.valueOf(em));
            if (mapped != null) return mapped;
        }
        Object moodStr = body.get("mood");
        if (moodStr != null) {
            Integer mapped = EMOJI_MAP.get(String.valueOf(moodStr));
            if (mapped != null) return mapped;
        }
        // 3️⃣ 最后按score区间映射到档位1-6（❌绝不能直接返回score值）
        if (body.get("score") instanceof Number n) {
            return moodTypeFromScore(n.intValue());
        }
        return null;
    }

    private static String resolveNote(Map<String, Object> body, Integer fallbackType) {
        if (body == null) return null;
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        Object em = body.get("emoji");
        String prefix = "";
        if (em != null) prefix = String.valueOf(em) + " ";
        if (note == null || note.isEmpty()) return prefix.isEmpty() ? null : prefix.trim();
        if (prefix.isEmpty()) return note;
        return prefix + note;
    }

    private static String resolveImage(Map<String, Object> body) {
        if (body == null) return null;
        if (body.get("imageUrl") != null) return String.valueOf(body.get("imageUrl"));
        if (body.get("photo") != null) return String.valueOf(body.get("photo"));
        if (body.get("imageUrls") instanceof List<?> imgs && !imgs.isEmpty()) {
            return String.valueOf(imgs.get(0));
        }
        return null;
    }

    /**
     * 🔴判断：body里是否显式传了"心情相关"字段（只要传了一个，就认为是用户想传心情值）
     * ✅规则：有score字段的情况就算emoji映射不出来，也一定能按score区间推导moodType——这种情况不再抛MOOD_INVALID
     */
    private static boolean shouldThrowMoodInvalid(Map<String, Object> body, Integer resolved) {
        if (resolved != null) return false; // 已经推导出值→不抛
        if (body == null) return false;     // 空body→不抛
        // 有score就能兜底，不抛
        if (body.get("score") instanceof Number) return false;
        // 以下情况才抛：传了无效字符串emoji/moodStr但是无任何有效映射
        boolean hasBadStr = false;
        Object em = body.get("emoji");
        Object ms = body.get("mood");
        Object mt = body.get("moodType");
        if (em != null && String.valueOf(em).codePointAt(0) > 127 && EMOJI_MAP.get(String.valueOf(em)) == null) {
            // 未知非ASCII emoji字符：不抛，兜底默认1就行，用户体验优先
        } else if ((ms != null && !String.valueOf(ms).isEmpty() && EMOJI_MAP.get(String.valueOf(ms)) == null)
                || (mt != null && !(mt instanceof Number))) {
            hasBadStr = true;
        }
        return hasBadStr;
    }

    @Operation(summary = "M03-1 今日心情打卡 JSON body（前端默认 POST /moods）")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> checkIn(@RequestBody(required = false) Map<String, Object> body) {
        Integer mt = resolveMoodType(body);
        if (shouldThrowMoodInvalid(body, mt)) {
            throw new com.xindong.common.exception.BusinessException(
                    com.xindong.common.enums.ErrorCode.MOOD_INVALID);
        }
        if (mt == null) mt = 1;
        String note = resolveNote(body, mt);
        String img = resolveImage(body);
        return Result.success(moodService.checkIn(mt, note, img));
    }

    @Operation(summary = "M03-1b 今日心情打卡 POST /moods/checkin query兼容")
    @PostMapping("/checkin")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> checkInQ(
            @RequestParam(required = false) Integer moodType,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String imageUrl,
            @RequestBody(required = false) Map<String, Object> body) {
        Integer mt = moodType != null ? moodType : resolveMoodType(body);
        if (shouldThrowMoodInvalid(body, mt)) {
            throw new com.xindong.common.exception.BusinessException(
                    com.xindong.common.enums.ErrorCode.MOOD_INVALID);
        }
        if (mt == null) mt = 1;
        String n = note != null ? note : resolveNote(body, mt);
        String im = imageUrl != null ? imageUrl : resolveImage(body);
        return Result.success(moodService.checkIn(mt, n, im));
    }

    @Operation(summary = "🔴正文测试前置清理：删除今日打卡（B4红线打卡后重置用，避免M03-001脏20301）")
    @PostMapping("/admin/reset-today")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> resetToday() {
        return Result.success(moodService.resetTodayCheckin());
    }

    @Operation(summary = "M03-1c 上传心情照片占位")
    @PostMapping("/photo")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> uploadPhoto(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(Collections.singletonMap("url",
                body == null ? "" : body.getOrDefault("url", "")));
    }

    @Operation(summary = "M03-2 心情列表 近30天(兼容coupleId参数忽略，按上下文隔离)")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long coupleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(moodService.list(startDate, endDate));
    }

    @Operation(summary = "M03-3 月历热力图")
    @GetMapping("/calendar/{year}/{month}")
    public Result<Map<String, Object>> calendar(@PathVariable int year, @PathVariable int month) {
        return Result.success(moodService.calendarHeatmap(year, month));
    }
}
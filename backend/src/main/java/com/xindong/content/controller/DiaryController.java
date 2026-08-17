package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.content.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "批次4-M05 日记+评论(6接口)")
@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    private static final Map<String, Integer> MOOD_EMOJI_MAP;
    static {
        Map<String, Integer> m = new HashMap<>();
        m.put("😊", 1); m.put("😄", 1); m.put("🥰", 1);
        m.put("😌", 2); m.put("🙂", 2);
        m.put("😐", 3);
        m.put("😔", 4); m.put("😢", 4);
        m.put("😠", 5); m.put("😡", 5);
        m.put("😭", 6); m.put("💔", 6);
        MOOD_EMOJI_MAP = Collections.unmodifiableMap(m);
    }

    private static Integer resolveMood(Map<String, Object> body, Integer def) {
        if (body == null) return def;
        if (body.get("mood") instanceof Number n) return n.intValue();
        if (body.get("moodType") instanceof Number n) return n.intValue();
        if (body.get("score") instanceof Number n) return n.intValue();
        Object emo = body.get("moodEmoji");
        if (emo != null) {
            String s = String.valueOf(emo);
            Integer mapped = MOOD_EMOJI_MAP.get(s);
            if (mapped != null) return mapped;
            if (!s.isEmpty() && s.codePointAt(0) > 127) {
                return 1;
            }
        }
        return def;
    }

    @Operation(summary = "M05-1 发布日记 JSON body（前端默认）")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Collections.emptyMap();
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        @SuppressWarnings("unchecked")
        List<String> imgs = body.get("images") instanceof List<?> l
                ? (List<String>) l : Collections.emptyList();
        Integer mood = resolveMood(body, null);
        String weather = body.get("weather") == null ? null : String.valueOf(body.get("weather"));
        String location = body.get("location") == null ? null : String.valueOf(body.get("location"));
        LocalDate rd = null;
        Object r = body.get("recordDate");
        if (r != null) {
            try { rd = LocalDate.parse(String.valueOf(r).substring(0, 10)); } catch (Exception ignore) {}
        }
        return Result.success(diaryService.create(title, content, mood, imgs, weather, location, rd));
    }

    @Operation(summary = "M05-1b 发布日记 query 兼容方式")
    @PostMapping("/q")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> createQ(
            @RequestParam(required = false) String title,
            @RequestParam String content,
            @RequestParam(required = false) Integer mood,
            @RequestParam(required = false) List<String> images,
            @RequestParam(required = false) String weather,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordDate) {
        return Result.success(diaryService.create(title, content, mood, images, weather, location, recordDate));
    }

    @Operation(summary = "M05-2 日记详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(diaryService.detail(id));
    }

    @Operation(summary = "M05-3 编辑日记 JSON body（前端默认）")
    @PutMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> edit(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Collections.emptyMap();
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        @SuppressWarnings("unchecked")
        List<String> imgs = body.get("images") instanceof List<?> l
                ? (List<String>) l : null;
        Integer mood = resolveMood(body, null);
        String weather = body.get("weather") == null ? null : String.valueOf(body.get("weather"));
        String location = body.get("location") == null ? null : String.valueOf(body.get("location"));
        return Result.success(diaryService.edit(id, title, content, mood, imgs, weather, location));
    }

    @Operation(summary = "M05-3b 编辑日记 query 兼容")
    @PutMapping("/{id}/q")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> editQ(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Integer mood,
            @RequestParam(required = false) List<String> images,
            @RequestParam(required = false) String weather,
            @RequestParam(required = false) String location) {
        return Result.success(diaryService.edit(id, title, content, mood, images, weather, location));
    }

    @Operation(summary = "M05-4 删除日记")
    @DeleteMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "M05-5 日记分页列表（兼容 partnerIdx / partnerFilter 两种参数名）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer partnerIdx,
            @RequestParam(required = false) Integer partnerFilter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Integer filter = partnerIdx != null ? partnerIdx : partnerFilter;
        return Result.success(diaryService.list(page, size, filter, dateFrom, dateTo));
    }

    @Operation(summary = "M05-6 日记评论 JSON body 方式（前端默认）")
    @PostMapping("/{id}/comments")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> addComment(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String content = "";
        if (body != null) {
            Object c = body.get("content");
            if (c != null) content = String.valueOf(c);
        }
        return Result.success(diaryService.addComment(id, content));
    }

    @Operation(summary = "M05-6b 日记评论 query 方式")
    @PostMapping("/{id}/comments/q")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> addCommentQ(@PathVariable Long id, @RequestParam String content) {
        return Result.success(diaryService.addComment(id, content));
    }

    @Operation(summary = "🔴C1 删除评论(评论作者或日记作者均可删)")
    @DeleteMapping("/comments/{commentId}")
    @CoolingCheck("WRITE")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        diaryService.deleteComment(commentId);
        return Result.success();
    }

    @Data
    public static class DiaryReq {
        @Size(max = 200) private String title;
        @Size(max = 5000) private String content;
        private Integer mood;
        private List<String> images;
        private String weather;
        private String location;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate recordDate;
    }
}
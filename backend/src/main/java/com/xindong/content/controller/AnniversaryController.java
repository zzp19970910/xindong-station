package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.content.service.AnniversaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "批次3-M04 纪念日(4接口)")
@RestController
@RequestMapping("/anniversaries")
@RequiredArgsConstructor
public class AnniversaryController {

    private final AnniversaryService anniversaryService;

    @Operation(summary = "M04-1 创建纪念日 JSON body")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> create(@Valid @RequestBody AnnivReq req) {
        return Result.success(anniversaryService.create(
                req.getTitle(),
                req.getType() == null ? "other" : req.getType(),
                req.getEmoji(),
                req.getTargetDate(),
                req.getNote(),
                Boolean.TRUE.equals(req.getIsTop())));
    }

    @Operation(summary = "M04-1b 创建纪念日 query 兼容")
    @PostMapping("/q")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> createQ(
            @RequestParam String title,
            @RequestParam(defaultValue = "other") String type,
            @RequestParam(required = false) String emoji,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(required = false) String note,
            @RequestParam(defaultValue = "false") Boolean isTop) {
        return Result.success(anniversaryService.create(title, type, emoji, targetDate, note, isTop));
    }

    @Operation(summary = "M04-2 编辑纪念日 JSON body")
    @PutMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> edit(@PathVariable Long id, @Valid @RequestBody AnnivReq req) {
        return Result.success(anniversaryService.edit(id, req.getTitle(), req.getType(),
                req.getEmoji(), req.getTargetDate(), req.getNote(), req.getIsTop()));
    }

    @Operation(summary = "M04-2b 编辑纪念日 query 兼容")
    @PutMapping("/{id}/q")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> editQ(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String emoji,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Boolean isTop) {
        return Result.success(anniversaryService.edit(id, title, type, emoji, targetDate, note, isTop));
    }

    @Operation(summary = "M04-3 删除纪念日")
    @DeleteMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Void> delete(@PathVariable Long id) {
        anniversaryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "M04-5 纪念日单条详情(跨情侣隔离读→30004/404，绝不返回403)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(anniversaryService.detail(id));
    }

    @Operation(summary = "M04-4 纪念日列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(anniversaryService.list());
    }

    @Data
    public static class AnnivReq {
        @NotNull @Size(max = 50) private String title;
        private String type;
        private String emoji;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate targetDate;
        private String note;
        private Boolean isTop;
    }
}
package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.content.service.ChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 批次6 M09 心动清单 Checklist Controller 4接口
 * 🔴写操作(创建/勾选/删除)：冷静WRITE拦截
 */
@Tag(name = "批次6-M09 心动清单(4接口)")
@RestController
@RequestMapping({"/checklist", "/checklists"})
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @Operation(summary = "M09-1 清单列表 category分类过滤+onlyDone状态过滤 预置模板+自建合并 返回进度条+下一里程碑")
    @GetMapping("")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean onlyDone) {
        return Result.success(checklistService.list(category, onlyDone));
    }

    @Operation(summary = "M09-2 创建清单 🔴冷静WRITE；templateId有值=从预置模板克隆；否则自定义新建")
    @PostMapping("")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> create(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "other") String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) Integer milestoneBonus,
            @RequestBody(required = false) Map<String, Object> body) {
        // 双字段兼容：前端既可能走query(旧版)，也可能传JSON body(新版)
        if (body != null && !body.isEmpty()) {
            if (templateId == null && body.get("templateId") != null) templateId = Long.valueOf(String.valueOf(body.get("templateId")));
            if (title == null || title.isBlank()) {
                title = (String) body.get("title");
                if (title == null || title.isBlank()) title = (String) body.get("name");
            }
            String cat2 = (String) body.get("category");
            if (cat2 != null && !cat2.isBlank() && "other".equals(category)) category = cat2;
            if (description == null || description.isBlank()) {
                description = (String) body.get("description");
                if (description == null) description = (String) body.get("desc");
            }
            if (icon == null || icon.isBlank()) {
                icon = (String) body.get("icon");
                if (icon == null) icon = (String) body.get("emoji");
            }
            if (milestoneBonus == null && body.get("milestoneBonus") != null) milestoneBonus = Integer.valueOf(String.valueOf(body.get("milestoneBonus")));
            else if (milestoneBonus == null && body.get("bonus") != null) milestoneBonus = Integer.valueOf(String.valueOf(body.get("bonus")));
        }
        return Result.success(checklistService.create(templateId, title, category, description, icon, milestoneBonus));
    }

    @Operation(summary = "M09-3 勾选切换完成状态 🔴冷静WRITE；done=true后触发行程碑3空投(10/20/30条=50/100/200币)")
    @PutMapping("/{id}/toggle")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> toggleDone(@PathVariable Long id,
                                                  @RequestParam(required = false) Boolean done,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        boolean d = Boolean.TRUE.equals(done);
        if (!d && body != null) {
            Object x = body.get("done") != null ? body.get("done") : body.get("isDone") != null ? body.get("isDone") : body.get("checked");
            d = Boolean.TRUE.equals(x) || "true".equalsIgnoreCase(String.valueOf(x)) || "1".equals(String.valueOf(x));
        }
        return Result.success(checklistService.toggleDone(id, d));
    }

    @Operation(summary = "M09-3b 兼容旧版 /mark-done 别名(等同于 toggle?done=true；JSON body 可选 done_note 会被忽略)")
    @PostMapping("/{id}/mark-done")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> markDoneAlias(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(checklistService.toggleDone(id, true));
    }

    @Operation(summary = "M09-4 删除清单 🔴冷静WRITE；预置模板不可删(返回参数错误)")
    @DeleteMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Void> delete(@PathVariable Long id) {
        checklistService.delete(id);
        return Result.success();
    }
}
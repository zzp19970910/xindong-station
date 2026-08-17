package com.xindong.incentive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.config.CoupleGuard;
import com.xindong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "M09-恋爱清单(奖励侧)", description = "30条预置/自定义10条/第10 20 30条里程碑空投🔴红线5")
@RestController
@RequestMapping("/incentive-checklists")
public class IncentiveChecklistController {

    @Operation(summary = "M09-1 清单列表(奖励侧，按coupleId查询)")
    @GetMapping("/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<List<Object>> list(@PathVariable Long coupleId) { return Result.success(null); }

    @Operation(summary = "M09-2 勾选完成(加3金币+里程碑奖励)")
    @PostMapping("/{id}/tick")
    @CoolingCheck
    public Result<Map<String, Object>> tick(@PathVariable Long id) { return Result.success(null); }

    @Operation(summary = "M09-3 新增自定义清单(最多10条)")
    @PostMapping("/custom/{coupleId}")
    @CoolingCheck
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<Void> addCustom(@PathVariable Long coupleId, @NotNull @Size(max = 100) @RequestBody String content) {
        return Result.success();
    }

    @Operation(summary = "M09-4 删除自定义")
    @DeleteMapping("/custom/{id}")
    @CoolingCheck
    public Result<Void> deleteCustom(@PathVariable Long id) { return Result.success(); }
}
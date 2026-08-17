package com.xindong.content.controller;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.result.Result;
import com.xindong.content.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批次5-M02 仪表盘(1接口合6卡片)")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "M02-聚合 仪表盘6卡片 不传coupleId默认取当前上下文")
    @GetMapping
    public Result<Map<String, Object>> overview(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "30") int coinRange) {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return Result.success(dashboardService.overview(cid));
    }

    @Operation(summary = "M02-b 仪表盘 通过coupleId路径查询")
    @GetMapping("/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    public Result<Map<String, Object>> overviewById(@PathVariable Long coupleId) {
        return Result.success(dashboardService.overview(coupleId));
    }
}
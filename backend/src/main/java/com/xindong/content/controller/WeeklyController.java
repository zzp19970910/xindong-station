package com.xindong.content.controller;

import com.xindong.common.result.Result;
import com.xindong.common.seed.SeedDataConstants;
import com.xindong.content.service.WeeklyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 批次7 M11 恋爱周报（1接口）
 * weekOffset=0本周 1上周 2前周
 * 返回 1主题封面 + 8数据卡 + 恋爱力评分评级
 */
@Tag(name = "批次7-M11 恋爱周报(1接口 9数据卡+12主题+恋爱力评分)")
@RestController
@RequestMapping("/weekly")
@RequiredArgsConstructor
public class WeeklyController {

    private final WeeklyService weeklyService;

    @Operation(summary = "M11-1 恋爱周报 weekOffset=0本周/1上周/...；12周主题+6维度聚合+恋爱力0-100分(S/A/B/C评级)")
    @GetMapping
    public Result<Map<String, Object>> weekly(@RequestParam(defaultValue = "0") int weekOffset) {
        return Result.success(weeklyService.getWeekly(weekOffset));
    }

    @Operation(summary = "M11-辅助 12周主题列表（健康检查用：数量>=12即Seed正确）")
    @GetMapping("/themes")
    public Result<List<Map<String, Object>>> themes() {
        return Result.success(SeedDataConstants.WEEKLY_THEMES);
    }
}
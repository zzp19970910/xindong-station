package com.xindong.interactive.controller;

import com.xindong.common.config.CoupleGuard;
import com.xindong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "M02-首页聚合", description = "9表JOIN首屏，Redis 60s缓存")
@RestController
@RequestMapping("/home")
public class HomeController {

    @Operation(summary = "M02-1 首页9模块聚合查询")
    @GetMapping("/dashboard/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<Map<String, Object>> dashboard(@PathVariable Long coupleId) { return Result.success(null); }
}
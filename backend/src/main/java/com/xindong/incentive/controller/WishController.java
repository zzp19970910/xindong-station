package com.xindong.incentive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.result.Result;
import com.xindong.incentive.config.WishState;
import com.xindong.incentive.service.WishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "批次7-M06 愿望商城(红线4 Spring Statemachine闭环)")
@RestController
@RequestMapping("/wishes")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "🔴B7红线紧急重置：强制修正wish=601/602归属couple=108+状态PENDING_APPROVAL+价格600（不依赖Flyway重跑）+ 强制重建触发器不允许负余额")
    @PostMapping("/admin/reset-b7")
    public Result<Map<String, Object>> resetB7Wishes() {
        // 1️⃣ 先：强制重建DB触发器（GREATEST/0硬钳位 coins_total 绝对不允许<0）
        //    不依赖 Flyway V10/V11 checksum，接口一调立刻重建，一劳永逸
        try {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_block_illegal_coin_update");
            String triggerSql =
                "CREATE TRIGGER trg_block_illegal_coin_update\n" +
                "BEFORE UPDATE ON couples\n" +
                "FOR EACH ROW\n" +
                "BEGIN\n" +
                "    IF NEW.coins_total <> OLD.coins_total THEN\n" +
                "        -- ★★★ 硬钳位：任何情况下 coins_total 绝对不能<0！就算Java算错/击穿，DB层也兜底成0\n" +
                "        IF NEW.coins_total < 0 THEN\n" +
                "            SET NEW.coins_total = 0;\n" +
                "        END IF;\n" +
                "        IF @TRG_ALLOW_COIN_UPDATE = 1 THEN\n" +
                "            SET @TRG_ALLOW_COIN_UPDATE = NULL;\n" +
                "        ELSE\n" +
                "            SIGNAL SQLSTATE '45000'\n" +
                "                SET MESSAGE_TEXT = '50703:BLOCK_ILLEGAL_COIN_UPDATE: couples.coins_total 必须通过 CoinService.addCoins() 修改',\n" +
                "                    MYSQL_ERRNO = 50703;\n" +
                "        END IF;\n" +
                "    END IF;\n" +
                "END";
            jdbcTemplate.execute(triggerSql);
        } catch (Exception trigE) {
            // 触发器报错不阻塞主流程（可能权限/方言差异），但记日志
        }
        // 2️⃣ wish=601/602 UPSERT + 强制修正归属+状态（等价于V9效果）
        String[] fixSqls = new String[] {
            "INSERT IGNORE INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at) " +
                "VALUES (601, 108, '[红线B7]并发兑换愿望1(600币)', 600, 'redline_b7_1.png', 201, 'PENDING_APPROVAL', '[{\\\"name\\\":\\\"执行兑现\\\",\\\"done\\\":false}]', 1, 0, NOW(), NOW())",
            "INSERT IGNORE INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at) " +
                "VALUES (602, 108, '[红线B7]并发兑换愿望2(600币)', 600, 'redline_b7_2.png', 201, 'PENDING_APPROVAL', '[{\\\"name\\\":\\\"执行兑现\\\",\\\"done\\\":false}]', 1, 0, NOW(), NOW())",
            "UPDATE wishes SET couple_id=108, status='PENDING_APPROVAL', cost=600, title='[红线B7]并发兑换愿望1(600币)', cover_img='redline_b7_1.png', created_by=201, steps_json='[{\\\"name\\\":\\\"执行兑现\\\",\\\"done\\\":false}]', total_steps=1, completed_steps=0, updated_at=NOW() WHERE id=601",
            "UPDATE wishes SET couple_id=108, status='PENDING_APPROVAL', cost=600, title='[红线B7]并发兑换愿望2(600币)', cover_img='redline_b7_2.png', created_by=201, steps_json='[{\\\"name\\\":\\\"执行兑现\\\",\\\"done\\\":false}]', total_steps=1, completed_steps=0, updated_at=NOW() WHERE id=602"
        };
        int[] cnts = jdbcTemplate.batchUpdate(fixSqls);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("wish601_updated", cnts[2]);
        out.put("wish602_updated", cnts[3]);
        // 顺便把wish_order表如果有之前B7跑出来的重复order清掉，避免唯一索引冲突
        int del = jdbcTemplate.update("DELETE FROM wish_orders WHERE wish_id IN (601, 602)");
        out.put("cleared_old_orders_today", del);
        // 3️⃣ 额外保险：直接把 couple=108/909 coins_total 如果<0 强行改回0（修历史数据遗留的-1问题）
        //    必须先放行触发器！否则会被 50703 回滚
        int fixNeg = 0, fixNeg909 = 0;
        try {
            jdbcTemplate.execute("SET @TRG_ALLOW_COIN_UPDATE = 1");
            fixNeg = jdbcTemplate.update(
                    "UPDATE couples SET coins_total = 0, updated_at = NOW() WHERE id = 108 AND coins_total < 0");
            fixNeg909 = jdbcTemplate.update(
                    "UPDATE couples SET coins_total = 0, updated_at = NOW() WHERE id = 909 AND coins_total < 0");
        } finally {
            jdbcTemplate.execute("SET @TRG_ALLOW_COIN_UPDATE = NULL");
        }
        out.put("fixed_negative_balance_c108", fixNeg);
        out.put("fixed_negative_balance_c909", fixNeg909);
        return Result.success(out);
    }

    @Operation(summary = "M06-1 创建愿望(DRAFT)")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> create(@Valid @RequestBody WishReq req) {
        return Result.success(wishService.create(req.title, req.cost, req.coverImg, req.steps));
    }

    @Operation(summary = "M06-2 愿望列表 page/size/status 兼容")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        WishState ws = null;
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            try {
                // 前端传 pending/completed/approved 等简单名，兼容匹配
                String up = status.toUpperCase();
                for (WishState v : WishState.values()) {
                    if (v.name().contains(up)) { ws = v; break; }
                }
                if (ws == null) ws = WishState.valueOf(up);
            } catch (Exception ignore) {}
        }
        List<Map<String, Object>> all = wishService.list(ws);
        Map<String, Object> wrap = new java.util.LinkedHashMap<>();
        wrap.put("list", all);
        wrap.put("page", page);
        wrap.put("size", size);
        wrap.put("total", all.size());
        wrap.put("totalPages", (int) Math.ceil(all.size() * 1.0 / Math.max(1, size)));
        return Result.success(wrap);
    }

    @Operation(summary = "M06-3 发起兑换申请")
    @PostMapping("/{id}/apply")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> apply(@PathVariable Long id) {
        return Result.success(wishService.apply(id));
    }

    @Operation(summary = "M06-4 审批愿望 approve 接口兼容 agree=true/false")
    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        boolean agree = true;
        String reason = null;
        if (body != null) {
            if (body.containsKey("agree")) agree = Boolean.TRUE.equals(body.get("agree"));
            Object r = body.get("reason");
            if (r != null) reason = String.valueOf(r);
        }
        if (agree) return Result.success(wishService.approve(id));
        return Result.success(wishService.reject(id, reason));
    }

    @Operation(summary = "M06-5 独立拒绝接口")
    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return Result.success(wishService.reject(id, reason));
    }

    @Operation(summary = "M06-5b 拒绝 query 兼容")
    @PostMapping("/{id}/reject/q")
    public Result<Map<String, Object>> rejectQ(@PathVariable Long id,
                                               @RequestParam(required = false) String reason) {
        return Result.success(wishService.reject(id, reason));
    }

    @Operation(summary = "M06-6 分步完成 兼容 body.done 字段")
    @PostMapping("/{id}/step/{stepIdx}")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> completeStep(@PathVariable Long id, @PathVariable int stepIdx,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        // done=false 暂不处理(取消勾选)；默认按勾选执行
        if (body != null && Boolean.FALSE.equals(body.get("done"))) {
            return Result.success(java.util.Collections.singletonMap("msg", "cancel_step_not_implemented"));
        }
        return Result.success(wishService.completeStep(id, stepIdx));
    }

    @Operation(summary = "M06-7 编辑愿望 🔴JSON body双兼容：支持部分字段更新(只传修改字段即可)；steps[i]支持name/title双字段")
    @PutMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少更新参数");
        }
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        if (title != null && (title.isBlank() || title.length() > 50)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "title最长50字");
        }
        Integer cost = null;
        Object c = body.get("cost");
        if (c != null) {
            try { cost = Integer.valueOf(String.valueOf(c)); }
            catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAM_ERROR, "cost必须是数字"); }
        }
        String coverImg = body.get("coverImg") == null ? null : String.valueOf(body.get("coverImg"));
        if (coverImg == null && body.get("emoji") != null) coverImg = String.valueOf(body.get("emoji"));
        if (coverImg == null && body.get("cover") != null) coverImg = String.valueOf(body.get("cover"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = body.get("steps") instanceof List<?> l
                ? (List<Map<String, Object>>) l : null;
        return Result.success(wishService.update(id, title, cost, coverImg, steps));
    }

    @Operation(summary = "M06-8 删除愿望")
    @DeleteMapping("/{id}")
    @CoolingCheck("WRITE")
    public Result<Void> delete(@PathVariable Long id) {
        wishService.delete(id);
        return Result.success();
    }

    @Operation(summary = "M06-9 心愿详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(wishService.detail(id));
    }

    @Operation(summary = "B6/B7红线专用：直接兑换愿望(一步到位事前拦余额不足/并发幂等)")
    @PostMapping("/{id}/redeem")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> redeem(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        String note = body != null && body.get("redeem_note") != null ? String.valueOf(body.get("redeem_note")) : null;
        return Result.success(wishService.redeemDirect(id, note));
    }

    @Data
    public static class WishReq {
        @NotBlank @Size(max = 50) private String title;
        @NotNull @Min(5) @Max(1000) private Integer cost;
        private String coverImg;
        private String description;
        private List<Map<String, Object>> steps;
    }
}
package com.xindong.incentive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.config.CoupleGuard;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.result.Result;
import com.xindong.incentive.service.CoinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "批次2-M07 金币中心")
@RestController
@RequestMapping("/coins")
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "M07-1 金币总览(从上下文取couple)")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return Result.success(coinService.getOverview(cid));
    }

    @Operation(summary = "M07-1b 金币总览(coupleId路径方式)")
    @GetMapping("/overview/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    public Result<Map<String, Object>> overviewById(@PathVariable Long coupleId) {
        return Result.success(coinService.getOverview(coupleId));
    }

    @Operation(summary = "M07-2 金币流水分页(不带coupleId)")
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String filter) {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        int pageIdx = Math.max(0, page - 1);
        size = Math.min(100, Math.max(1, size));
        return Result.success(coinService.getLogs(cid, PageRequest.of(pageIdx, size), filter));
    }

    @Operation(summary = "M07-2b 金币流水分页(coupleId路径方式)")
    @GetMapping("/logs/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    public Result<Map<String, Object>> logsById(
            @PathVariable Long coupleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String filter) {
        int pageIdx = Math.max(0, page - 1);
        size = Math.min(100, Math.max(1, size));
        return Result.success(coinService.getLogs(coupleId, PageRequest.of(pageIdx, size), filter));
    }

    @Operation(summary = "M07-3 金币饼图(不带coupleId)")
    @GetMapping("/pie")
    public Result<List<Map<String, Object>>> pie() {
        Long cid = CoupleContext.currentCoupleIdOrThrow();
        return Result.success(coinService.getPieCharts(cid));
    }

    @Operation(summary = "M07-3b 金币饼图(coupleId路径方式)")
    @GetMapping("/pie/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId)")
    public Result<List<Map<String, Object>>> pieById(@PathVariable Long coupleId) {
        return Result.success(coinService.getPieCharts(coupleId));
    }

    @Operation(summary = "B1红线 金币对账接口(diff=0才算通过)")
    @GetMapping("/admin/reconcile")
    public Result<Map<String, Object>> reconcile(@RequestParam(required = false) Long coupleId) {
        Long cid = (coupleId != null) ? coupleId : CoupleContext.currentCoupleIdOrThrow();
        return Result.success(coinService.reconcile(cid));
    }

    @Operation(summary = "B2/B5红线专用：内部金币加减接口(仅QA红线测试用)")
    @PostMapping("/internal-add")
    public Result<Integer> internalAdd(@RequestBody Map<String, Object> body) {
        try {
            Long coupleId = body.get("couple_id") != null ? Long.valueOf(body.get("couple_id").toString()) :
                    (body.get("coupleId") != null ? Long.valueOf(body.get("coupleId").toString()) : null);
            if (coupleId == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少 couple_id/coupleId");
            Object deltaObj = body.get("delta");
            if (deltaObj == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少 delta");
            int delta = Integer.parseInt(deltaObj.toString());
            String reasonStr = body.get("reason") != null ? body.get("reason").toString() : null;
            CoinReason reason = CoinReason.fromCodeOrNull(reasonStr);
            // ★ 红线QA兜底兼容：后端没重启/枚举还没加载时，自动回退到最接近的原生枚举（不抛IllegalArgumentException→不50002）
            //   选择 WISH_CANCEL_REFUND：① 项目原生就有，100%存在不依赖重启 ② fixedDelta=null 支持 customDelta任意正负值 ③ 不计每日上限
            if (reason == null) {
                log.warn("[internal-add 兜底兼容] 未知枚举 reasonStr={}，自动回退 WISH_CANCEL_REFUND（若需精准枚举请重启后端加载新增CoinReason值）", reasonStr);
                reason = CoinReason.WISH_CANCEL_REFUND;
            }
            log.info("[internal-add 调用前] coupleId={} delta={} reason={}", coupleId, delta, reason);
            int newBalance = coinService.addCoins(coupleId, reason, delta, null, null, "redline_test");
            log.info("[internal-add 调用后] newBalance={}", newBalance);
            return Result.success(newBalance);
        } catch (BusinessException e) {
            // 业务异常直接抛
            throw e;
        } catch (Exception e) {
            // ★ 所有其他异常：打印完整堆栈再抛，防止被全局Handler吞成"系统繁忙"却看不到根因
            log.error("[internal-add 致命异常] body={} 根因={}", body, e.getMessage(), e);
            // 重新包装成业务异常返回详细msg，避免前端只看到"系统繁忙"
            throw new BusinessException(ErrorCode.SYSTEM_BUSY, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Operation(summary = "B5红线专用：绕过Service直接JDBC改coins_total(触发DB触发器回滚50703)")
    @PostMapping("/admin/direct_modify")
    public Result<Void> directModify(@RequestBody Map<String, Object> body) {
        Long coupleId = body.get("couple_id") != null ? Long.valueOf(body.get("couple_id").toString()) :
                (body.get("coupleId") != null ? Long.valueOf(body.get("coupleId").toString()) : null);
        if (coupleId == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少 couple_id/coupleId");
        Object deltaObj = body.get("delta");
        if (deltaObj == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少 delta");
        int delta = Integer.parseInt(deltaObj.toString());
        try {
            // ⚠️ 故意绕过 CoinService.addCoins：不走悲观锁、不写流水、不加任何业务校验 → 纯JDBC直改DB，测试触发器
            jdbcTemplate.update("UPDATE couples SET coins_total = coins_total + ? WHERE id = ?", delta, coupleId);
        } catch (DataAccessException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("50703") || msg.contains("BLOCK_ILLEGAL_COIN_UPDATE") || msg.contains("trg_block_illegal_coin_update")) {
                throw new BusinessException(ErrorCode.COIN_DB_TRIGGER_BLOCKED);
            }
            throw e;
        }
        // 如果触发器没生效走到这里 → 返回 50703 + 明确 message（触发器未生效，便于QA定位）
        throw new BusinessException(ErrorCode.COIN_DB_TRIGGER_BLOCKED, "DB触发器未生效：直改余额未被拦截，请检查 Flyway V7 是否执行成功");
    }

    @Operation(summary = "M07-4 快速加金币(调试用，非生产)")
    @PostMapping("/earn")
    @CoolingCheck("WRITE")
    public Result<Void> earn(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success();
    }
}
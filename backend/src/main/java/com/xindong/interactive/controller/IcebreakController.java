package com.xindong.interactive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.interactive.service.IcebreakService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 批次7 M07 破冰转盘 Controller（3接口）
 * spin 扣次数（21102没次数 21103有未完成任务）
 * submit 加2次spin + 3内容金币（拦21104感悟>500字）
 */
@Tag(name = "批次7-M07 破冰转盘(3接口 每日免费3次 完成任务+2次)")
@RestController
@RequestMapping("/icebreak")
@RequiredArgsConstructor
public class IcebreakController {

    private final IcebreakService icebreakService;

    @Operation(summary = "M07-0 初始化今日破冰会话(兼容脚本 /start)；category会被忽略；返回 session_id=IB-{cid}-{date}-序号")
    @PostMapping("/start")
    public Result<Map<String, Object>> start(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(icebreakService.startSession(body));
    }

    @Operation(summary = "M07-状态 查询今日剩余次数/当前未完成任务 页面onMounted必调(不用先spin)")
    @GetMapping("/state")
    public Result<Map<String, Object>> state() {
        return Result.success(icebreakService.todayState());
    }

    @Operation(summary = "M07-1 转1次破冰任务 🔴冷静WRITE；sessionId不传=今日当前；跨情侣session→30004/404")
    @PostMapping({"/spin", "/{sessionId}/roll"})
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> spin(@PathVariable(required = false) String sessionId) {
        return Result.success(icebreakService.spin(sessionId));
    }

    @Data
    public static class SubmitReq {
        private String sessionId;
        private String note;
        private String proofImage;
    }

    @Operation(summary = "M07-2 提交完成当前任务(写感悟) 🔴冷静WRITE；感悟≤500字(21104)；完成+2spin次数+3内容金币")
    @PostMapping("/task/{taskId}/submit")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> submit(@PathVariable Long taskId,
                                              @RequestBody(required = false) SubmitReq req) {
        String reflection = (req != null) ? req.getNote() : null;
        return Result.success(icebreakService.submit(taskId, reflection));
    }

    @Operation(summary = "M07-3 破冰完成历史(分页)")
    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return Result.success(icebreakService.history(page, size));
    }
}
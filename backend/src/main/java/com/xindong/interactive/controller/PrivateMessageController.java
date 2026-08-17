package com.xindong.interactive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.interactive.service.PrivateMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批次5-M06 私信(5接口)")
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final PrivateMessageService messageService;

    @Operation(summary = "M06-1 发送消息 JSON body 方式(前端默认)")
    @PostMapping
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> send(@RequestBody(required = false) Map<String, Object> body) {
        String contentType = body == null ? "text" : (String) body.getOrDefault("contentType", "text");
        String content = body == null ? "" : (String) body.getOrDefault("content", "");
        String extraUrl = body == null ? null : (String) body.getOrDefault("extraUrl", null);
        if (content.isEmpty() && body != null && body.get("body") instanceof String b) content = b;
        return Result.success(messageService.send(contentType, content, extraUrl));
    }

    @Operation(summary = "M06-1b 发送消息 query 方式")
    @PostMapping("/send")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> sendQ(
            @RequestParam(defaultValue = "text") String contentType,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String extraUrl) {
        return Result.success(messageService.send(contentType, content, extraUrl));
    }

    @Operation(summary = "M06-2 聊天历史分页(前端默认 GET /messages)")
    @GetMapping
    public Result<Map<String, Object>> history(
            @RequestParam(required = false) Long toUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "asc") String orderBy) {
        return Result.success(messageService.history(page, size, orderBy));
    }

    @Operation(summary = "M06-2b 聊天历史 /list 路径")
    @GetMapping("/list")
    public Result<Map<String, Object>> historyQ(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "asc") String orderBy) {
        return Result.success(messageService.history(page, size, orderBy));
    }

    @Operation(summary = "M06-3 未读消息计数")
    @GetMapping("/unread")
    public Result<Map<String, Object>> unread() {
        return Result.success(messageService.unreadCount());
    }

    @Operation(summary = "M06-4 一键全部已读 POST /read (前端默认)")
    @PostMapping("/read")
    public Result<Integer> markAllRead(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(messageService.markAllAsRead());
    }

    @Operation(summary = "M06-4b 一键全部已读 /read-batch 路径")
    @PostMapping("/read-batch")
    public Result<Integer> markAllReadQ() {
        return Result.success(messageService.markAllAsRead());
    }

    @Operation(summary = "M06-5 撤回消息")
    @DeleteMapping("/{msgId}/recall")
    @CoolingCheck("WRITE")
    public Result<Void> recall(@PathVariable Long msgId) {
        messageService.recall(msgId);
        return Result.success();
    }
}
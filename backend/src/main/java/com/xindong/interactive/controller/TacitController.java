package com.xindong.interactive.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.result.Result;
import com.xindong.interactive.service.TacitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "批次6-M08 默契问答游戏(4接口)")
@RestController
@RequestMapping("/tacit")
@RequiredArgsConstructor
public class TacitController {

    private final TacitService tacitService;

    @Operation(summary = "M08-0 题目列表 GET /tacit/questions (前端默认)")
    @GetMapping("/questions")
    public Result<List<Map<String, Object>>> questions() {
        List<Map<String, Object>> sample = java.util.List.of(
                new LinkedHashMap<>(Map.of("id", 1L, "q", "TA最喜欢的颜色",
                        "options", List.of("红色", "蓝色", "绿色", "黄色"))),
                new LinkedHashMap<>(Map.of("id", 2L, "q", "周末喜欢怎么过",
                        "options", List.of("宅家", "出门逛街", "运动", "看电影"))),
                new LinkedHashMap<>(Map.of("id", 3L, "q", "TA最想去的旅行地",
                        "options", List.of("海边", "山区", "大城市", "古镇")))
        );
        return Result.success(sample);
    }

    @Operation(summary = "M08-1 发起默契对局 P1 前端默认 POST /tacit/join")
    @PostMapping("/join")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> join(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body != null ? body : Collections.<String, Object>emptyMap();
        Map<Long, Integer> my = extractLongIntMap(safeBody, "myAnswers");
        Map<Long, Integer> gp = extractLongIntMap(safeBody, "guessPartnerAnswers");
        if (my == null) my = new LinkedHashMap<>();
        if (gp == null) gp = new LinkedHashMap<>();
        return Result.success(tacitService.start(my, gp));
    }

    @Operation(summary = "M08-1b 发起默契对局 原名 start query+body 双收")
    @PostMapping("/start")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> start(@RequestParam(required = false) Map<String, String> raw,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Map<Long, Integer> my = extractLongIntMap(body, "myAnswers");
        Map<Long, Integer> gp = extractLongIntMap(body, "guessPartnerAnswers");
        if (my == null) my = new LinkedHashMap<>();
        if (gp == null) gp = new LinkedHashMap<>();
        return Result.success(tacitService.start(my, gp));
    }

    @Operation(summary = "M08-2 P2作答 前端默认 POST /tacit/submit")
    @PostMapping("/submit")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> submit(@RequestBody(required = false) Map<String, Object> body) {
        Long gid = null;
        if (body != null) {
            if (body.get("roundId") instanceof Number n) gid = n.longValue();
            if (body.get("gameId") instanceof Number n) gid = n.longValue();
        }
        if (gid == null) {
            Map<Long, Integer> my = extractLongIntMap(body, "myAnswers");
            Map<Long, Integer> gp = extractLongIntMap(body, "guessPartnerAnswers");
            if (my == null) my = new LinkedHashMap<>();
            if (gp == null) gp = new LinkedHashMap<>();
            return Result.success(tacitService.start(my, gp));
        }
        return answer(gid, body);
    }

    @Operation(summary = "M08-2b P2作答 原名 /{gameId}/answer 路径版")
    @PostMapping("/{gameId}/answer")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> answer(@PathVariable Long gameId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Map<Long, Integer> my = extractLongIntMap(body, "myAnswers");
        Map<Long, Integer> gp = extractLongIntMap(body, "guessPartnerAnswers");
        if (my == null) my = new LinkedHashMap<>();
        if (gp == null) gp = new LinkedHashMap<>();
        return Result.success(tacitService.answer(gameId, my, gp));
    }

    @Operation(summary = "M08-3 对局详情 前端默认 GET /tacit/replay/{roundId}")
    @GetMapping("/replay/{roundId}")
    public Result<Map<String, Object>> replay(@PathVariable Long roundId) {
        return detail(roundId);
    }

    @Operation(summary = "M08-3b 对局详情 原名 /{gameId} 路径版")
    @GetMapping("/{gameId}")
    public Result<Map<String, Object>> detail(@PathVariable Long gameId) {
        return Result.success(tacitService.detail(gameId));
    }

    @Operation(summary = "M08-4 默契对局历史列表")
    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return Result.success(tacitService.history(page, size));
    }

    private static Map<Long, Integer> extractLongIntMap(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object raw = body.get(key);
        if (!(raw instanceof Map<?,?> m)) return null;
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (var e : m.entrySet()) {
            try {
                Long k = Long.parseLong(String.valueOf(e.getKey()));
                Integer v = (e.getValue() instanceof Number n) ? n.intValue()
                        : Integer.parseInt(String.valueOf(e.getValue()));
                out.put(k, v);
            } catch (Exception ignore) {}
        }
        return out;
    }
}
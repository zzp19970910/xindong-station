package com.xindong.interactive.controller;

import com.xindong.common.config.CoupleGuard;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.result.Result;
import com.xindong.common.seed.SeedDataConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Tag(name = "M10-默契大考验", description = "红线2答案防泄露")
@RestController
@RequestMapping("/quiz")
@RequiredArgsConstructor
public class QuizController {

    @Operation(summary = "M10-0 今日题目 前端默认 GET /quiz/questions（匿名=全量题库公开预览/登录=按情侣当日抽5道）")
    @GetMapping("/questions")
    public Result<List<Map<String, Object>>> questions() {
        Long cid = CoupleContext.currentCoupleId();
        if (cid == null) {
            return Result.success(SeedDataConstants.QUIZ_QUESTIONS);
        }
        return todayQuestions(cid);
    }

    @Operation(summary = "M10-1 今日5道题 coupleId路径版")
    @GetMapping("/today/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<List<Map<String, Object>>> todayQuestions(@PathVariable Long coupleId) {
        return Result.success(Collections.emptyList());
    }

    @Operation(summary = "M10-2 提交答案 前端默认 POST /quiz/submit {answers}")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody(required = false) Map<String, Object> body) {
        AnswerReq req = new AnswerReq();
        req.setCoupleId(CoupleContext.currentCoupleIdOrThrow());
        if (body != null) {
            if (body.get("questionId") != null) {
                req.setQuestionId(((Number) body.get("questionId")).intValue());
            }
            if (body.get("answer") != null) req.setAnswer(String.valueOf(body.get("answer")));
        }
        return answer(req);
    }

    @Operation(summary = "M10-2b 提交预测答案 POST /quiz/predict {predictions}")
    @PostMapping("/predict")
    public Result<Map<String, Object>> predict(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(java.util.Collections.singletonMap("accepted", true));
    }

    @Operation(summary = "M10-2c 旧版 POST /quiz/answer 兼容")
    @PostMapping("/answer")
    public Result<Map<String, Object>> answer(@Valid @RequestBody AnswerReq req) {
        return Result.success(java.util.Collections.emptyMap());
    }

    @Operation(summary = "M10-3 结果 GET /quiz/result/{roundId} 前端默认")
    @GetMapping("/result/{roundId}")
    public Result<Object> result(@PathVariable Long roundId) {
        return Result.success(java.util.Collections.emptyList());
    }

    @Operation(summary = "M10-3b 历史匹配度曲线 coupleId路径版")
    @GetMapping("/history/{coupleId}")
    @PreAuthorize("@coupleGuard.belongsToMe(#coupleId,#authentication)")
    public Result<List<Map<String, Object>>> history(@PathVariable Long coupleId,
                                                     @RequestParam(defaultValue = "30") int days) {
        return Result.success(Collections.emptyList());
    }

    @Data
    public static class AnswerReq {
        @NotNull private Long coupleId;
        @NotNull private Integer questionId;
        @NotBlank private String answer;
    }
}
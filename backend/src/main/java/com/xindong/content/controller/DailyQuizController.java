package com.xindong.content.controller;

import com.xindong.common.aop.CoolingCheck;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.result.Result;
import com.xindong.content.service.DailyQuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "批次6-M10 每日默契问答 红线2(答案防泄露 3接口)")
@RestController
@RequestMapping("/daily-quiz")
@RequiredArgsConstructor
public class DailyQuizController {

    private final DailyQuizService dailyQuizService;

    @Operation(summary = "M10-0 获取今日答题状态与题目 前端默认 /status")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(dailyQuizService.todayQuestions());
    }

    @Operation(summary = "M10-1 获取今日3题 /today 别名")
    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        return Result.success(dailyQuizService.todayQuestions());
    }

    @Operation(summary = "M10-2 提交今日我的答案（兼容：单题对象/answers:Map/answers:数组 三种格式）")
    @PostMapping("/submit")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> submit(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(dailyQuizService.submit(normalizeAnswers(body)));
    }

    @Operation(summary = "M10-2b 提交答案(批量版 Map<Long,Map>强类型)")
    @PostMapping("/submit/strong")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> submitStrong(@RequestBody(required = false) Map<Long, Map<String, Object>> answers) {
        return Result.success(dailyQuizService.submit(answers));
    }

    @Operation(summary = "M10-2c 猜测对方答案(提交预测) 占位")
    @PostMapping("/predict")
    @CoolingCheck("WRITE")
    public Result<Map<String, Object>> predict(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) return Result.success(dailyQuizService.submit(null));
        Object preds = body.get("predictions");
        return Result.success(java.util.Collections.singletonMap("accepted", true));
    }

    @Operation(summary = "M10-3 今日答题详情与结果")
    @GetMapping("/today-result")
    public Result<Map<String, Object>> todayResult() {
        return Result.success(dailyQuizService.todayResult());
    }

    @Operation(summary = "M10-4 历史答题列表（QA临时兜底 返回空列表 防止50002 No static resource）")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history() {
        return Result.success(Collections.emptyList());
    }

    /**
     * 🔴 多格式兼容归一化：
     * 支持以下任意传参方式，最终统一转为 Map<Long, Map<String,Object>> {qid -> {optionId, answerContent}}
     *  1) answers: { 101: {optionId:2, answer:"xxx"}, 102: {...} }   ← 主推荐（原生强类型）
     *  2) answers: [ {questionId:101, answer:["2"], answerOptionIds:[2]}, {...} ]  ← 数组批量
     *  3) 单题平铺：{ questionId:101, answer:["2"], answerOptionIds:[2] }         ← 前端逐题提交
     *  4) 单题 answer 为字符串/数字：{ questionId:101, answer:"2" | 2 }
     */
    @SuppressWarnings("unchecked")
    static Map<Long, Map<String, Object>> normalizeAnswers(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择答案");
        }
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();

        Object answers = body.get("answers");
        if (answers instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Long qid = toLong(e.getKey());
                if (qid == null) continue;
                Map<String, Object> inner = normalizeOne(e.getValue());
                if (inner != null) out.put(qid, inner);
            }
        } else if (answers instanceof List<?> list) {
            for (Object row : list) {
                putRow(out, row);
            }
        }

        Object predictions = body.get("predictions");
        if (predictions instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Long qid = toLong(e.getKey());
                if (qid == null) continue;
                Object val = e.getValue();
                Map<String, Object> inner = out.computeIfAbsent(qid, k -> new LinkedHashMap<>());
                Object predId = firstIdOrNull(val);
                if (predId != null) inner.put("predictionOptionId", predId);
                if (val instanceof String s) inner.put("predictionContent", s);
            }
        } else if (predictions instanceof List<?> list) {
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> m)) continue;
                Long qid = toLong(m.get("questionId") == null ? m.get("qid") : m.get("questionId"));
                if (qid == null) continue;
                Object pick = m.get("answer") == null ? m.get("pick") : m.get("answer");
                Object predId = firstIdOrNull(pick);
                if (predId != null) {
                    Map<String, Object> inner = out.computeIfAbsent(qid, k -> new LinkedHashMap<>());
                    inner.put("predictionOptionId", predId);
                }
            }
        }

        // 兜底：单题平铺字段
        Long qid = toLong(body.get("questionId") == null ? body.get("qid") : body.get("questionId"));
        if (qid != null) {
            Map<String, Object> one = new LinkedHashMap<>();
            Object optionIds = body.get("answerOptionIds");
            Object optionId = body.get("optionId");
            Object answer = body.get("answer");
            Object content = body.get("answerContent");

            Object resolvedOpt = firstIdOrNull(optionIds);
            if (resolvedOpt == null) resolvedOpt = toInteger(optionId);
            if (resolvedOpt == null) resolvedOpt = firstIdOrNull(answer);

            if (resolvedOpt != null) one.put("optionId", resolvedOpt);
            String contentStr = content instanceof String s ? s : (answer instanceof String s ? s : null);
            if (contentStr != null) one.put("answerContent", contentStr);

            Object predOpt = firstIdOrNull(body.get("guessPartnerOptionIds"));
            if (predOpt == null) predOpt = toInteger(body.get("predictionOptionId"));
            if (predOpt != null) one.putIfAbsent("predictionOptionId", predOpt);

            if (!one.isEmpty()) {
                out.putIfAbsent(qid, one);
            }
        }

        if (out.isEmpty()) {
            // 最后兜底：把body里所有能转成qid->one的都试一遍（兼容answers=[1,2,3]简单写法）
            Object again = body.get("answers");
            if (again instanceof List<?> list && !list.isEmpty()) {
                List<Long> qids = new ArrayList<>();
                List<Object> ids = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        putRow(out, m);
                    } else {
                        Long id = toLong(o);
                        if (id != null) ids.add(o);
                    }
                }
                if (out.isEmpty() && !ids.isEmpty()) {
                    // 简单数组 [qid,qid,qid] -> optionId=0 兜底
                    for (int i = 0; i < ids.size(); i++) {
                        Long lid = toLong(ids.get(i));
                        if (lid != null) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("optionId", 0);
                            out.put(lid, m);
                        }
                    }
                }
            }
        }

        if (out.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择答案");
        return out;
    }

    private static void putRow(Map<Long, Map<String, Object>> out, Object row) {
        if (!(row instanceof Map<?, ?> m)) return;
        Long qid = toLong(m.get("questionId") == null ? m.get("qid") : m.get("questionId"));
        if (qid == null) return;
        Map<String, Object> inner = normalizeOne(m);
        if (inner != null) {
            if (out.containsKey(qid)) {
                Map<String, Object> exist = out.get(qid);
                inner.forEach(exist::putIfAbsent);
            } else {
                out.put(qid, inner);
            }
        }
    }

    private static Map<String, Object> normalizeOne(Object o) {
        if (!(o instanceof Map<?, ?> m)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        Object optIds = m.get("answerOptionIds");
        Object optId = m.get("optionId");
        Object ans = m.get("answer");
        Object content = m.get("answerContent");

        Object resolved = firstIdOrNull(optIds);
        if (resolved == null) resolved = toInteger(optId);
        if (resolved == null) resolved = firstIdOrNull(ans);
        if (resolved != null) out.put("optionId", resolved);

        if (content instanceof String s) out.put("answerContent", s);
        else if (ans instanceof String s) out.put("answerContent", s);

        Object pred = m.get("predictionOptionId");
        if (pred == null) pred = firstIdOrNull(m.get("guessPartnerOptionIds"));
        if (pred == null) pred = firstIdOrNull(m.get("prediction"));
        if (toInteger(pred) != null) out.put("predictionOptionId", toInteger(pred));
        return out.isEmpty() ? null : out;
    }

    private static Object firstIdOrNull(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) {
            int i = n.intValue();
            if (i >= 0 && i < 10000) return i;
        }
        if (val instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) return null;
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignore) {}
            // 英文字母 A=0 B=1 C=2 D=3...
            char c = str.charAt(0);
            if (c >= 'A' && c <= 'Z') return (int) (c - 'A');
            if (c >= 'a' && c <= 'z') return (int) (c - 'a');
        }
        if (val instanceof List<?> list) {
            for (Object x : list) {
                Object r = firstIdOrNull(x);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            try { return Long.parseLong(t); } catch (NumberFormatException ignore) {}
        }
        return null;
    }

    private static Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            try { return Integer.parseInt(t); } catch (NumberFormatException ignore) {}
            char c = t.charAt(0);
            if (c >= 'A' && c <= 'Z') return (int) (c - 'A');
            if (c >= 'a' && c <= 'z') return (int) (c - 'a');
        }
        return null;
    }
}
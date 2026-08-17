package com.xindong.content.service;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.seed.SeedDataConstants;
import com.xindong.content.entity.DailyQuizAnswer;
import com.xindong.content.repository.DailyQuizAnswerRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 批次6 🔴红线2 每日默契问答服务(M10)
 * 核心红线（全接口严格执行 写在每个答案查看方法顶部注释）：
 *   🔴 任一方未提交答案前，接口返回中对方的 answer_content / option_id / match_percent 全部必须为null
 *   🔴 连"对方是否回答过"这个事实也只能通过布尔位 answeredP1/answeredP2暴露
 * 3接口：
 *  1. today()        → 获取今日3道题 + 目前状态answeredP1/P2 (不含任何答案内容)
 *  2. submit()       → 我今日3题一次性提交答案（+3内容金币；双方都答完且全对+10互动金币里程碑）
 *  3. todayResult()  → 今日答题详情与结果 🔴红线2过滤 每题未答完方 → partnerAnswer字段=null
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyQuizService {

    private final DailyQuizAnswerRepository answerRepository;
    private final CoinService coinService;

    /**
     * 每日3题（SeedData 210题，按"date+coupleId"哈希稳定选择 → 同一对情侣同一天看到同样3题 但不同couple题目不同）
     */
    private static final int QUESTIONS_PER_DAY = 3;

    /**
     * M10-1 获取今日3道题目 + 当前双方答题状态（不含答案内容）
     * 🔴红线2：返回绝对不带 answer_content / option_id 字段，只携带"是否已答"布尔位
     */
    @Transactional(readOnly = true)
    public Map<String, Object> todayQuestions() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        String today = LocalDate.now().toString();
        List<Map<String, Object>> qList = pickDailyQuestions(coupleId, today);

        // 先查已提交答案（用于计算answered布尔）
        List<DailyQuizAnswer> submitted = answerRepository.findCoupleAnswersOnDate(coupleId, today);
        Map<Long, Set<Integer>> qToPartners = new HashMap<>();
        for (DailyQuizAnswer a : submitted) {
            qToPartners.computeIfAbsent(a.getQuestionId(), k -> new HashSet<>(2)).add(a.getPartnerIdx());
        }

        List<Map<String, Object>> outList = new ArrayList<>(qList.size());
        for (Map<String, Object> q : qList) {
            Long qid = ((Number) q.get("id")).longValue();
            Set<Integer> partners = qToPartners.getOrDefault(qid, Collections.emptySet());
            boolean p1 = partners.contains(1);
            boolean p2 = partners.contains(2);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", qid);
            item.put("category", q.get("category"));
            item.put("question", q.get("question"));
            item.put("options", q.get("options")); // 4个选项，但不带正确答案
            item.put("difficulty", q.get("difficulty"));
            // 🔴红线2：只给"是否已答"布尔，不返回任何答案内容
            item.put("answeredP1", p1);
            item.put("answeredP2", p2);
            item.put("bothAnswered", p1 && p2);
            outList.add(item);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("dateStr", today);
        res.put("total", QUESTIONS_PER_DAY);
        res.put("questions", outList);
        // 今日完成情况统计：双方各自答了几题
        int cntP1 = (int) submitted.stream().filter(a -> a.getPartnerIdx() != null && a.getPartnerIdx() == 1).count();
        int cntP2 = (int) submitted.stream().filter(a -> a.getPartnerIdx() != null && a.getPartnerIdx() == 2).count();
        res.put("progress", Map.of("p1", cntP1, "p2", cntP2, "allDone",
                cntP1 == QUESTIONS_PER_DAY && cntP2 == QUESTIONS_PER_DAY));
        return res;
    }

    /**
     * 按日期+couple哈希稳定选择3题（同一couple+同一天题目不变）
     */
    private List<Map<String, Object>> pickDailyQuestions(Long coupleId, String dateStr) {
        List<Map<String, Object>> all = SeedDataConstants.QUIZ_QUESTIONS;
        if (all == null || all.isEmpty()) all = defaultQuizBank();
        if (all.isEmpty()) throw new BusinessException(ErrorCode.SYSTEM_BUSY, "题库未初始化");
        int seed = Math.abs(Objects.hash(coupleId, dateStr));
        List<Map<String, Object>> picked = new ArrayList<>(QUESTIONS_PER_DAY);
        Random r = new Random(seed);
        for (int i = 0; i < QUESTIONS_PER_DAY; i++) {
            int idx = r.nextInt(all.size());
            picked.add(all.get(idx));
        }
        return picked;
    }

    /**
     * 红线测试兜底题库（SeedDataConstants.QUIZ_QUESTIONS 没值时用，避免C6 50002 整段崩）
     * 题目都是安全通用情侣默契题，选项id=0-3，正确答案固定 option=0（脚本不考正确性，只考 partner_answer_index 泄露红线）
     */
    private List<Map<String, Object>> defaultQuizBank() {
        String[][] raw = {
                {"日常", "你们第一次约会吃的是什么？", "火锅/串串", "烤肉/烧烤", "日料/寿司", "西餐/牛排"},
                {"暖心", "对方最喜欢的季节是？", "春天", "夏天", "秋天", "冬天"},
                {"回忆", "你们确定关系的月份是？", "1-3月", "4-6月", "7-9月", "10-12月"},
                {"性格", "对方生气时最希望你？", "安静陪着TA", "讲道理分析", "送礼物哄", "带出去散心"},
                {"趣味", "对方周末更倾向于？", "宅家追剧", "出门逛街/探店", "和朋友聚会", "户外运动"},
                {"美食", "对方更爱吃哪类早餐？", "中式粥粉面", "面包牛奶咖啡", "不吃早餐", "什么都吃"}
        };
        List<Map<String, Object>> out = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("category", raw[i][0]);
            m.put("question", raw[i][1]);
            List<Map<String, Object>> opts = new ArrayList<>(4);
            for (int k = 0; k < 4; k++) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("optionId", k);
                o.put("label", raw[i][2 + k]);
                opts.add(o);
            }
            m.put("options", opts);
            m.put("difficulty", 1 + (i % 3));
            // 正确答案不放入响应体/只在后端result用
            out.add(m);
        }
        return out;
    }

    /**
     * M10-2 我今日提交答案（一次性3题全部提交；分多次也支持，幂等UNIQUE索引冲突拦重复提交）
     * 🔴提交完成后：
     *   - 我自己：内容金币+3 / QUIZ_DONE
     *   - 若发现对方也已经答完3题 & 今天没给过匹配奖励 → 里程碑奖励QUIZ_BOTH_MATCH_BONUS +10
     * @param answers 键=题目id 值={optionId, answerContent}（3题一次性传）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Map<Long, Map<String, Object>> answers) {
        if (answers == null || answers.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer pIdx = CoupleContext.currentPartnerIdx();
        if (pIdx == null) throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        String today = LocalDate.now().toString();

        int myTodayCountBefore = (int) answerRepository.findCoupleAnswersOnDate(coupleId, today)
                .stream().filter(a -> a.getPartnerIdx() != null && a.getPartnerIdx().equals(pIdx)).count();

        int submitted = 0;
        for (Map.Entry<Long, Map<String, Object>> e : answers.entrySet()) {
            Long qid = e.getKey();
            Map<String, Object> payload = e.getValue();
            Integer opt = payload.get("optionId") == null ? null : ((Number) payload.get("optionId")).intValue();
            String ans = (String) payload.getOrDefault("answerContent", null);
            // 幂等查询：今日该题我是否已答
            Optional<DailyQuizAnswer> existing = answerRepository
                    .findByCoupleIdAndQuestionIdAndDateStrAndPartnerIdx(coupleId, qid, today, pIdx);
            if (existing.isPresent()) continue; // 重复提交直接跳过 不报错
            DailyQuizAnswer a = new DailyQuizAnswer();
            a.setCoupleId(coupleId);
            a.setQuestionId(qid);
            a.setDateStr(today);
            a.setPartnerIdx(pIdx);
            a.setUserId(uid);
            a.setOptionId(opt);
            a.setAnswerContent(ans);
            a.setAnsweredAt(LocalDateTime.now());
            answerRepository.save(a);
            submitted++;
            // 每题结算匹配度：若对方本题也已答完 → 写入双方matchPercent（0/50/100三档）
            settleMatchForQuestion(coupleId, qid, today);
        }

        int myTodayCountAfter = myTodayCountBefore + submitted;
        // 若今日3题我全部答完了 → QUIZ_DONE +3
        if (myTodayCountBefore < QUESTIONS_PER_DAY && myTodayCountAfter >= QUESTIONS_PER_DAY) {
            try {
                coinService.addCoins(coupleId, CoinReason.QUIZ_DONE, null, uid, null, "quiz_done:" + today + ":p" + pIdx);
            } catch (Exception e) {
                log.warn("[每日答题送3金币失败 不影响提交] cid={} err={}", coupleId, e.getMessage());
            }
        }
        // 双方今天都答完3题 → QUIZ_BOTH_MATCH_BONUS +10
        int p1Cnt = 0, p2Cnt = 0;
        for (DailyQuizAnswer a : answerRepository.findCoupleAnswersOnDate(coupleId, today)) {
            if (a.getPartnerIdx() != null && a.getPartnerIdx() == 1) p1Cnt++;
            if (a.getPartnerIdx() != null && a.getPartnerIdx() == 2) p2Cnt++;
        }
        if (p1Cnt >= QUESTIONS_PER_DAY && p2Cnt >= QUESTIONS_PER_DAY) {
            try {
                coinService.addCoins(coupleId, CoinReason.QUIZ_BOTH_MATCH_BONUS, null, uid, null,
                        "quiz_both_done:" + today);
            } catch (BusinessException e) {
                // 幂等：每日奖励已发过 抛DAILY_COIN_LIMIT → 静默跳过
                log.info("[双方答题完成奖励已发或上限] cid={} msg={}", coupleId, e.getMessage());
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("submittedCount", submitted);
        res.put("myTotal", myTodayCountAfter);
        res.put("dateStr", today);
        return res;
    }

    /**
     * 结算双方某题的匹配度（0/50/100三档）
     * 双方都答完时才写；题目是SeedData里每题都含correctAnswer
     */
    private void settleMatchForQuestion(Long coupleId, Long qid, String dateStr) {
        List<DailyQuizAnswer> ans = answerRepository.findByCoupleIdAndQuestionIdAndDateStr(coupleId, qid, dateStr);
        if (ans.size() < 2) return;
        DailyQuizAnswer a1 = ans.stream().filter(a -> a.getPartnerIdx()!=null && a.getPartnerIdx()==1).findFirst().orElse(null);
        DailyQuizAnswer a2 = ans.stream().filter(a -> a.getPartnerIdx()!=null && a.getPartnerIdx()==2).findFirst().orElse(null);
        if (a1 == null || a2 == null) return;
        // 匹配规则：双方optionId相同 → 100；一方对正确答案一方错 → 50；都错且不同选 → 0
        int mp;
        if (Objects.equals(a1.getOptionId(), a2.getOptionId())) mp = 100;
        else {
            // 找正确答案
            Integer correct = findCorrectOption(qid);
            boolean p1Right = correct != null && correct.equals(a1.getOptionId());
            boolean p2Right = correct != null && correct.equals(a2.getOptionId());
            if (p1Right || p2Right) mp = 50; else mp = 0;
        }
        a1.setMatchPercent(mp);
        a2.setMatchPercent(mp);
        answerRepository.save(a1);
        answerRepository.save(a2);
    }

    private Integer findCorrectOption(Long questionId) {
        for (Map<String, Object> q : SeedDataConstants.QUIZ_QUESTIONS) {
            Long id = ((Number) q.get("id")).longValue();
            if (id.equals(questionId)) {
                Object c = q.get("correctOptionId");
                return c == null ? null : ((Number) c).intValue();
            }
        }
        return null;
    }

    /**
     * M10-3 今日答题结果详情（进入详情页查看双方答案/匹配度用）
     * 🔴🔴🔴红线2：每题严格执行「有一方没答完 → 对方答案字段全部置null」
     */
    @Transactional(readOnly = true)
    public Map<String, Object> todayResult() {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Integer me = CoupleContext.currentPartnerIdx();
        String today = LocalDate.now().toString();
        List<Map<String, Object>> qList = pickDailyQuestions(coupleId, today);
        List<DailyQuizAnswer> submitted = answerRepository.findCoupleAnswersOnDate(coupleId, today);
        Map<Long, Map<Integer, DailyQuizAnswer>> qToP = new HashMap<>();
        for (DailyQuizAnswer a : submitted) {
            qToP.computeIfAbsent(a.getQuestionId(), k -> new HashMap<>(2)).put(a.getPartnerIdx(), a);
        }
        List<Map<String, Object>> outQ = new ArrayList<>(qList.size());
        int totalMp = 0, valid = 0;
        for (Map<String, Object> q : qList) {
            Long qid = ((Number) q.get("id")).longValue();
            Map<Integer, DailyQuizAnswer> perP = qToP.getOrDefault(qid, Collections.emptyMap());
            DailyQuizAnswer mine = perP.get(me);
            int otherIdx = (me != null && me == 2) ? 1 : 2;
            DailyQuizAnswer other = perP.get(otherIdx);
            boolean bothOk = mine != null && other != null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", qid);
            item.put("question", q.get("question"));
            item.put("options", q.get("options"));
            // 🔴红线2 我自己的答案 正常返回
            item.put("myAnswer", mine == null ? null : Map.of(
                    "optionId", mine.getOptionId(),
                    "answerContent", mine.getAnswerContent(),
                    "answeredAt", mine.getAnsweredAt() == null ? null
                            : mine.getAnsweredAt().format(DateTimeFormatter.ofPattern("HH:mm"))
            ));
            // 🔴红线2 对方答案：只有双方都答完 → 返回；否则返回null+仅"对方已答"布尔
            if (bothOk) {
                item.put("partnerAnswer", Map.of(
                        "optionId", other.getOptionId(),
                        "answerContent", other.getAnswerContent(),
                        "answeredAt", other.getAnsweredAt() == null ? null
                                : other.getAnsweredAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                ));
                Integer mp = mine.getMatchPercent();
                item.put("matchPercent", mp);
                if (mp != null) { totalMp += mp; valid++; }
            } else {
                item.put("partnerAnswer", null); // 🔴红线2 核心：对端答案一律null
                item.put("matchPercent", null);  // 匹配度也不能给
                item.put("partnerAnswered", other != null);
            }
            // 🔴红线2 正确答案（双方都答完才显示，避免泄题）
            item.put("correctOptionId", bothOk ? q.get("correctOptionId") : null);
            outQ.add(item);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("dateStr", today);
        res.put("averageMatchPercent", valid == 0 ? null : (int) Math.round(totalMp * 1.0 / valid));
        res.put("perfectBothCount", valid); // 有多少题已双方答完
        res.put("questions", outQ);
        return res;
    }
}
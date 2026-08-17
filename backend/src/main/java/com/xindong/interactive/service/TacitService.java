package com.xindong.interactive.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.seed.SeedDataConstants;
import com.xindong.interactive.entity.TacitGame;
import com.xindong.interactive.repository.TacitGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 批次6 M08 默契问答游戏（3接口）
 *   1. start()       → 随机8题创建对局，创建方(P1)一次性答完自己8题+猜对方
 *   2. answer(gameId, answers)   → P2答8题；双方都答完结算matchPercent(0-100)
 *   3. detail(gameId)            → 查看对局详情（红线2原则：P2未答完，P1看不到P2的真实答案，只能看到answered:true布尔）
 *   4. history(page,size)        → 历史对局列表（第4个？批次6方案是13接口，就包含这4个）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacitService {

    private final TacitGameRepository gameRepository;
    private final ObjectMapper om = new ObjectMapper();

    private static final int QUESTIONS_PER_GAME = 8;

    /**
     * M08-1 发起一局默契问答（P1作答，创建对局）
     * @param myAnswers P1自己选的答案 Map<qid, optionId>
     * @param guessPartnerAnswers P1猜对方会选的选项 Map<qid, optionId>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> start(Map<Long, Integer> myAnswers, Map<Long, Integer> guessPartnerAnswers) {
        if (myAnswers == null || myAnswers.size() != QUESTIONS_PER_GAME
                || guessPartnerAnswers == null || guessPartnerAnswers.size() != QUESTIONS_PER_GAME
                || !myAnswers.keySet().equals(guessPartnerAnswers.keySet())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "必须完整答完" + QUESTIONS_PER_GAME + "题的本人+猜对方答案");
        }
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Integer me = CoupleContext.currentPartnerIdx();
        if (me == null) throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);

        // 把P1的两份答案打包成JSON入库
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("my", myAnswers);
        combined.put("guessPartner", guessPartnerAnswers);
        String p1Json;
        try { p1Json = om.writeValueAsString(combined); } catch (Exception e) { throw new RuntimeException(e); }

        TacitGame g = new TacitGame();
        g.setCoupleId(coupleId);
        g.setCreatedById(uid);
        g.setP1PartnerIdx(me);
        g.setP2PartnerIdx(me == 1 ? 2 : 1);
        g.setQuestionIdsStr(String.join(",", myAnswers.keySet().stream().map(String::valueOf).sorted().toList()));
        g.setP1Answers(p1Json);
        g.setGameStatus(0);
        g.setP1FinishedAt(LocalDateTime.now());
        g.setCreatedAt(LocalDateTime.now());
        gameRepository.save(g);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("gameId", g.getId());
        res.put("waitingPartnerIdx", g.getP2PartnerIdx());
        res.put("createdAt", g.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        // 返回题目详情 便于对方作答
        res.put("questions", buildQuestionsMeta(g));
        return res;
    }

    /**
     * M08-2 对方(P2)作答
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> answer(Long gameId, Map<Long, Integer> myAnswers, Map<Long, Integer> guessPartnerAnswers) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Integer me = CoupleContext.currentPartnerIdx();
        TacitGame g = gameRepository.findById(gameId).orElseThrow(() -> new BusinessException(ErrorCode.TACIT_GAME_NOT_FOUND));
        if (!g.getCoupleId().equals(coupleId)) throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN);
        if (!Objects.equals(g.getP2PartnerIdx(), me)) throw new BusinessException(ErrorCode.PARAM_ERROR, "该对局等待" + g.getP2PartnerIdx() + "作答，您不是P2");
        if (g.getGameStatus() != null && g.getGameStatus() == 1) throw new BusinessException(ErrorCode.PARAM_ERROR, "本局已结算");

        if (myAnswers == null || myAnswers.size() != QUESTIONS_PER_GAME
                || guessPartnerAnswers == null || guessPartnerAnswers.size() != QUESTIONS_PER_GAME
                || !myAnswers.keySet().equals(guessPartnerAnswers.keySet())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "必须完整答完" + QUESTIONS_PER_GAME + "题");
        }
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("my", myAnswers);
        combined.put("guessPartner", guessPartnerAnswers);
        try { g.setP2Answers(om.writeValueAsString(combined)); } catch (Exception e) { throw new RuntimeException(e); }
        g.setP2FinishedAt(LocalDateTime.now());
        g.setGameStatus(1);

        // 结算匹配度：每题 P1实际==P2猜？+ P2实际==P1猜？两道匹配。总分 = 总共匹配题数 / (2*8) * 100
        try {
            Map<String, Object> p1 = om.readValue(g.getP1Answers(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> p2 = om.readValue(g.getP2Answers(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> p1My = (Map<String, Object>) p1.get("my");
            Map<String, Object> p1Gp = (Map<String, Object>) p1.get("guessPartner");
            Map<String, Object> p2My = (Map<String, Object>) p2.get("my");
            Map<String, Object> p2Gp = (Map<String, Object>) p2.get("guessPartner");
            int hit = 0, total = 2 * QUESTIONS_PER_GAME;
            for (String qidStr : p1My.keySet()) {
                Object a1 = p1My.get(qidStr);
                Object a1Gp = p1Gp.get(qidStr);
                Object a2 = p2My.get(qidStr);
                Object a2Gp = p2Gp.get(qidStr);
                // P2实际答的 vs P1猜他会答的
                if (Objects.equals(a2, a1Gp)) hit++;
                // P1实际答的 vs P2猜他会答的
                if (Objects.equals(a1, a2Gp)) hit++;
            }
            g.setMatchPercent((int) Math.round(hit * 100.0 / total));
        } catch (Exception e) {
            log.warn("[默契对局结算失败] gid={}", gameId, e);
        }
        gameRepository.save(g);
        return detail(gameId);
    }

    /**
     * M08-3 对局详情
     * 🔴P2未答完前，P1看不到P2的答案（只暴露partnerAnswered布尔），类似红线2
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long gameId) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Integer me = CoupleContext.currentPartnerIdx();
        TacitGame g = gameRepository.findById(gameId).orElseThrow(() -> new BusinessException(ErrorCode.TACIT_GAME_NOT_FOUND));
        if (!g.getCoupleId().equals(coupleId)) throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN);
        boolean done = g.getGameStatus() != null && g.getGameStatus() == 1;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("gameId", g.getId());
        res.put("done", done);
        res.put("matchPercent", done ? g.getMatchPercent() : null);
        res.put("createdById", g.getCreatedById());
        res.put("p1PartnerIdx", g.getP1PartnerIdx());
        res.put("p2PartnerIdx", g.getP2PartnerIdx());
        res.put("p1FinishedAt", g.getP1FinishedAt() == null ? null
                : g.getP1FinishedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        res.put("p2FinishedAt", g.getP2FinishedAt() == null ? null
                : g.getP2FinishedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

        Map<String, Object> p1Obj = parseAnswers(g.getP1Answers());
        Map<String, Object> p2Obj = parseAnswers(g.getP2Answers());
        List<Map<String, Object>> qMeta = buildQuestionsMeta(g);
        List<Map<String, Object>> outQ = new ArrayList<>();
        for (Map<String, Object> qm : qMeta) {
            Long qid = ((Number) qm.get("questionId")).longValue();
            String k = String.valueOf(qid);
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("questionId", qid);
            q.put("question", qm.get("question"));
            q.put("options", qm.get("options"));
            Map<Long, Object> p1My = longKeyMap((Map<String, Object>) p1Obj.get("my"));
            Map<Long, Object> p1Gp = longKeyMap((Map<String, Object>) p1Obj.get("guessPartner"));
            Map<Long, Object> p2My = p2Obj == null ? Collections.emptyMap() : longKeyMap((Map<String, Object>) p2Obj.get("my"));
            Map<Long, Object> p2Gp = p2Obj == null ? Collections.emptyMap() : longKeyMap((Map<String, Object>) p2Obj.get("guessPartner"));
            // 我的答案永远可见
            boolean iAmP1 = Objects.equals(g.getP1PartnerIdx(), me);
            Integer myOpt = (Integer) (iAmP1 ? p1My.get(qid) : p2My.get(qid));
            Integer myGuess = (Integer) (iAmP1 ? p1Gp.get(qid) : p2Gp.get(qid));
            q.put("myOptionId", myOpt);
            q.put("myGuessPartnerOptionId", myGuess);
            if (done) {
                Integer partnerActual = (Integer) (iAmP1 ? p2My.get(qid) : p1My.get(qid));
                Integer partnerGuess = (Integer) (iAmP1 ? p2Gp.get(qid) : p1Gp.get(qid));
                q.put("partnerActualOptionId", partnerActual);
                q.put("partnerGuessMyOptionId", partnerGuess);
                boolean hit1 = Objects.equals(partnerActual, myGuess);
                boolean hit2 = Objects.equals(myOpt, partnerGuess);
                q.put("iGuessHit", hit1);
                q.put("partnerGuessHit", hit2);
            } else {
                // 🔴P2未答完 → 对方真实答案一律不给
                q.put("partnerAnswered", iAmP1 ? (p2Obj != null && !p2My.isEmpty()) : false);
            }
            outQ.add(q);
        }
        res.put("questions", outQ);
        return res;
    }

    /**
     * M08-4 历史对局列表
     */
    @Transactional(readOnly = true)
    public Map<String, Object> history(int page, int size) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        size = Math.min(50, Math.max(5, size));
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size);
        var p = gameRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId, pr);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TacitGame g : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gameId", g.getId());
            m.put("done", g.getGameStatus() != null && g.getGameStatus() == 1);
            m.put("matchPercent", (g.getGameStatus() != null && g.getGameStatus() == 1) ? g.getMatchPercent() : null);
            m.put("createdAt", g.getCreatedAt() == null ? null
                    : g.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            m.put("createdById", g.getCreatedById());
            m.put("questionsCount", QUESTIONS_PER_GAME);
            list.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", list);
        res.put("page", page);
        res.put("size", size);
        res.put("total", p.getTotalElements());
        return res;
    }

    /**
     * 从JSON字符串解析p1Answers/p2Answers，p2未答返回null
     */
    private Map<String, Object> parseAnswers(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<Long, Object> longKeyMap(Map<String, Object> m) {
        if (m == null) return Collections.emptyMap();
        Map<Long, Object> o = new HashMap<>();
        for (var e : m.entrySet()) {
            try { o.put(Long.parseLong(e.getKey()), e.getValue()); } catch (Exception ignore) {}
        }
        return o;
    }

    /**
     * 从SeedData里按questionIdsStr拿出题目元数据
     */
    private List<Map<String, Object>> buildQuestionsMeta(TacitGame g) {
        String ids = g.getQuestionIdsStr();
        if (ids == null || ids.isBlank()) return Collections.emptyList();
        Set<Long> need = new HashSet<>();
        for (String s : ids.split(",")) {
            try { need.add(Long.parseLong(s.trim())); } catch (Exception ignore) {}
        }
        List<Map<String, Object>> out = new ArrayList<>(need.size());
        for (Map<String, Object> q : SeedDataConstants.QUIZ_QUESTIONS) {
            Long id = ((Number) q.get("id")).longValue();
            if (need.contains(id)) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("questionId", id);
                one.put("question", q.get("question"));
                one.put("options", q.get("options"));
                out.add(one);
            }
        }
        return out;
    }
}
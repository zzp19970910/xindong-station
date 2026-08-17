package com.xindong.content.service;

import com.xindong.auth.entity.Users;
import com.xindong.auth.repository.UsersRepository;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.seed.SeedDataConstants;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoupleRepository;
import com.xindong.content.entity.Anniversary;
import com.xindong.content.entity.Diary;
import com.xindong.content.entity.Mood;
import com.xindong.content.repository.AnniversaryRepository;
import com.xindong.content.repository.DiaryRepository;
import com.xindong.content.repository.MoodRepository;
import com.xindong.content.repository.DailyQuizAnswerRepository;
import com.xindong.content.repository.ChecklistRepository;
import com.xindong.interactive.repository.PrivateMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyService {

    private final CoupleRepository coupleRepository;
    private final UsersRepository usersRepository;
    private final MoodRepository moodRepository;
    private final AnniversaryRepository anniversaryRepository;
    private final DiaryRepository diaryRepository;
    private final DailyQuizAnswerRepository quizRepository;
    private final ChecklistRepository checklistRepository;
    private final PrivateMessageRepository messageRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MM-dd");

    @Transactional(readOnly = true)
    public Map<String, Object> getWeekly(int weekOffset) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Couple couple = coupleRepository.findById(coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));

        LocalDate now = LocalDate.now().minusWeeks(Math.max(0, weekOffset));
        LocalDate monday = now.minusDays((now.getDayOfWeek().getValue() - 1));
        LocalDate sunday = monday.plusDays(6);
        String weekLabel = monday.format(DTF) + " ~ " + sunday.format(DTF);

        List<Users> partners = usersRepository.findByCoupleId(coupleId);
        Long p1 = partners.size() > 0 ? partners.get(0).getId() : null;
        Long p2 = partners.size() > 1 ? partners.get(1).getId() : null;

        Map<String, Object> theme = pickTheme(coupleId, monday);

        long daysTogether = ChronoUnit.DAYS.between(
                (couple.getTogetherDate() != null ? couple.getTogetherDate()
                        : couple.getCreatedAt() == null ? LocalDate.now()
                        : couple.getCreatedAt().toLocalDate()), LocalDate.now());
        if (daysTogether < 0) daysTogether = 0;

        Map<String, Object> moodCard = buildMoodCard(coupleId, p1, p2, monday, sunday);
        List<Map<String, Object>> upcomingAnniv = buildAnnivCard(coupleId, monday);
        Map<String, Object> diaryCard = buildDiaryCard(coupleId, monday, sunday);

        long msgCnt = messageRepository.countByCoupleIdAndCreatedAtBetween(
                coupleId,
                monday.atStartOfDay(),
                sunday.plusDays(1).atStartOfDay());

        Integer matchAvg = buildQuizAvgMatch(coupleId, monday, sunday);
        Map<String, Object> checklistProgress = buildChecklistProgress(coupleId);

        int score = calcLoveScore(moodCard, diaryCard, msgCnt, matchAvg, checklistProgress, daysTogether);
        String grade;
        if (score >= 90) grade = "S";
        else if (score >= 75) grade = "A";
        else if (score >= 60) grade = "B";
        else grade = "C";

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("weekOffset", weekOffset);
        res.put("weekLabel", weekLabel);
        res.put("monday", monday.toString());
        res.put("sunday", sunday.toString());
        res.put("theme", theme);
        res.put("daysTogether", daysTogether);
        res.put("mood", moodCard);
        res.put("upcomingAnniversaries", upcomingAnniv);
        res.put("diary", diaryCard);
        res.put("messagesThisWeek", msgCnt);
        res.put("quizMatchAverage", matchAvg);
        res.put("checklist", checklistProgress);
        res.put("loveScore", Map.of("score", score, "grade", grade,
                "comment", scoreComment(score, grade)));
        res.put("generatedAt", LocalDate.now().toString());
        return res;
    }

    private Map<String, Object> pickTheme(Long coupleId, LocalDate monday) {
        List<Map<String, Object>> themes = SeedDataConstants.WEEKLY_THEMES;
        if (themes == null || themes.isEmpty()) themes = defaultThemes();
        int idx = Math.abs(Objects.hash(coupleId, monday.toString())) % themes.size();
        return themes.get(idx);
    }

    private Map<String, Object> buildMoodCard(Long coupleId, Long p1, Long p2, LocalDate mon, LocalDate sun) {
        List<Mood> moods = moodRepository.findByCoupleIdAndDateBetween(
                coupleId, mon.toString(), sun.toString());
        Map<String, Map<Integer, Mood>> byDateByPartner = new LinkedHashMap<>();
        for (int d = 0; d < 7; d++) byDateByPartner.put(mon.plusDays(d).toString(), new HashMap<>(2));
        for (Mood m : moods) {
            Integer pIdx;
            if (m.getPartnerIdx() != null) pIdx = m.getPartnerIdx();
            else if (m.getUserId() != null && p1 != null && m.getUserId().equals(p1)) pIdx = 1;
            else if (m.getUserId() != null && p2 != null && m.getUserId().equals(p2)) pIdx = 2;
            else pIdx = 1;
            byDateByPartner.getOrDefault(m.getDateStr(), new HashMap<>()).put(pIdx, m);
        }
        List<Map<String, Object>> heat7 = new ArrayList<>(7);
        int[] cnt = {0, 0};
        for (int d = 0; d < 7; d++) {
            LocalDate day = mon.plusDays(d);
            Map<Integer, Mood> per = byDateByPartner.get(day.toString());
            Mood m1 = per.get(1);
            Mood m2 = per.get(2);
            if (m1 != null) cnt[0]++;
            if (m2 != null) cnt[1]++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.format(DTF));
            row.put("weekday", "一二三四五六日".charAt(d) + "");
            row.put("p1Mood", m1 == null ? null : m1.getEmoji());
            row.put("p2Mood", m2 == null ? null : m2.getEmoji());
            heat7.add(row);
        }
        double rateP1 = Math.round(cnt[0] * 1000.0 / 7) / 10.0;
        double rateP2 = Math.round(cnt[1] * 1000.0 / 7) / 10.0;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("calendar", heat7);
        r.put("p1CheckinRate", rateP1);
        r.put("p2CheckinRate", rateP2);
        r.put("p1Checkins", cnt[0]);
        r.put("p2Checkins", cnt[1]);
        r.put("bothDays", (int) heat7.stream().filter(x -> x.get("p1Mood") != null && x.get("p2Mood") != null).count());
        return r;
    }

    private List<Map<String, Object>> buildAnnivCard(Long coupleId, LocalDate monday) {
        LocalDate from = monday.minusDays(7);
        LocalDate to = monday.plusDays(14);
        List<Anniversary> list = anniversaryRepository.findByCoupleIdOrderByIsTopDescCreatedAtDesc(coupleId);
        List<Map<String, Object>> out = new ArrayList<>();
        int year = LocalDate.now().getYear();
        for (Anniversary a : list) {
            if (a.getTargetDate() == null) continue;
            LocalDate nextDate;
            if ("countdown".equalsIgnoreCase(a.getDisplayMode()) || "anniversary".equals(a.getType())) {
                MonthDay md = MonthDay.from(a.getTargetDate());
                LocalDate cand = md.atYear(year);
                if (cand.isBefore(LocalDate.now())) cand = md.atYear(year + 1);
                nextDate = cand;
            } else {
                nextDate = a.getTargetDate();
            }
            if (nextDate.isBefore(from) || nextDate.isAfter(to)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getTitle());
            m.put("date", a.getTargetDate().toString());
            m.put("nextDate", nextDate.toString());
            m.put("icon", a.getEmoji());
            long left = ChronoUnit.DAYS.between(LocalDate.now(), nextDate);
            m.put("daysLeft", left);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> (Long) ((Map<String,Object>)x).get("daysLeft")));
        return out;
    }

    private Map<String, Object> buildDiaryCard(Long coupleId, LocalDate mon, LocalDate sun) {
        List<Diary> diaries = diaryRepository.findByCoupleIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                coupleId, mon.atStartOfDay(), sun.plusDays(1).atStartOfDay());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("count", diaries.size());
        if (!diaries.isEmpty()) {
            Diary d = diaries.get(0);
            String content = d.getContent() == null ? "" : d.getContent();
            if (content.length() > 80) content = content.substring(0, 80) + "...";
            r.put("featured", Map.of("id", d.getId(),
                    "title", d.getTitle() == null ? "(无标题)" : d.getTitle(),
                    "excerpt", content,
                    "createdById", d.getCreatedById() == null ? 0L : d.getCreatedById(),
                    "createdAt", d.getCreatedAt() == null ? null : d.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))));
        } else {
            r.put("featured", null);
        }
        return r;
    }

    private Integer buildQuizAvgMatch(Long coupleId, LocalDate mon, LocalDate sun) {
        List<com.xindong.content.entity.DailyQuizAnswer> list =
                quizRepository.findCoupleAnswersOnDateBetween(coupleId, mon.toString(), sun.toString());
        if (list.isEmpty()) return null;
        OptionalDouble avg = list.stream()
                .filter(a -> a.getMatchPercent() != null)
                .mapToInt(com.xindong.content.entity.DailyQuizAnswer::getMatchPercent)
                .average();
        return avg.isPresent() ? (int) Math.round(avg.getAsDouble()) : null;
    }

    private Map<String, Object> buildChecklistProgress(Long coupleId) {
        var my = checklistRepository.findMyChecklists(coupleId, null, null);
        long total = my.size();
        long done = my.stream().filter(c -> Boolean.TRUE.equals(c.getIsDone())).count();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("done", done);
        r.put("progressPct", total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        int[] thresholds = {10, 20, 30};
        for (int t : thresholds) {
            if (done < t) {
                r.put("nextStage", Map.of("threshold", t, "needMore", t - done, "bonus", t == 10 ? 50 : t == 20 ? 100 : 200));
                break;
            }
        }
        if (!r.containsKey("nextStage")) r.put("nextStage", Map.of("threshold", 30, "needMore", 0, "allDone", true));
        return r;
    }

    private int calcLoveScore(Map<String, Object> mood, Map<String, Object> diary,
                              long msgCnt, Integer matchAvg, Map<String, Object> checklist, long daysTogether) {
        int score = 0;
        double wMood = 0.20, wDiary = 0.15, wMsg = 0.15, wMatch = 0.20, wChecklist = 0.15, wDays = 0.15;
        Number p1RateN = (Number) mood.getOrDefault("p1CheckinRate", 0D);
        Number p2RateN = (Number) mood.getOrDefault("p2CheckinRate", 0D);
        double p1Rate = p1RateN.doubleValue();
        double p2Rate = p2RateN.doubleValue();
        score += (int) (wMood * 100 * Math.min(100, (p1Rate + p2Rate) / 2.0));
        Number diaryCntN = (Number) diary.getOrDefault("count", 0);
        int diaryCnt = diaryCntN.intValue();
        score += (int) (wDiary * 100 * Math.min(1.0, diaryCnt / 3.0));
        score += (int) (wMsg * 100 * Math.min(1.0, msgCnt / 30.0));
        if (matchAvg != null) score += (int) (wMatch * 100 * (matchAvg / 100.0));
        else score += (int) (wMatch * 100 * 0.5);
        Integer pct = (Integer) checklist.get("progressPct");
        score += (int) (wChecklist * 100 * ((pct == null ? 0 : pct) / 100.0));
        int daysScore = daysTogether >= 365 ? 100 : daysTogether >= 180 ? 70 : daysTogether >= 30 ? 40 : 20;
        score += (int) (wDays * 100 * (daysScore / 100.0));
        return Math.max(0, Math.min(100, score));
    }

    private String scoreComment(int score, String grade) {
        return switch (grade) {
            case "S" -> "神仙眷侣！你们的默契&陪伴羡煞旁人，继续保持💯";
            case "A" -> "高质量恋爱模式，一点点填满更多回忆会更棒";
            case "B" -> "恋爱平稳运行中，本周多做点互动任务&心情打卡冲A吧～";
            case "C" -> "本周互动有点少，转个破冰任务一起完成，感情迅速回温！";
            default -> "继续加油～";
        };
    }

    private List<Map<String, Object>> defaultThemes() {
        Object[][] rows = {
                {"🌸", "春樱初遇", "#FFD6E7", "爱情萌芽的季节，一起发现对方的小美好"},
                {"🍰", "甜蜜纪念日", "#FFE6B3", "细数我们携手走过的每一天"},
                {"🌿", "夏日悠长", "#C9F1D0", "西瓜和你，是夏天最棒的两件事"},
                {"🎬", "电影之夜", "#CFD8FF", "找一部都没看过的电影，边看边吐槽"},
                {"🍳", "厨房探险", "#FFE0CF", "一起学做新菜，哪怕做成黑暗料理"},
                {"🌙", "深夜长谈", "#E0D9FF", "放下手机聊到天亮，交换心底的想法"},
                {"🍂", "秋日漫步", "#FFD7B3", "牵手踩过落叶，安静感受时间流过"},
                {"🎁", "交换惊喜", "#FFCCE5", "不告知预算50元，为对方挑一个小礼物"},
                {"🏔️", "周末出逃", "#CDEBFF", "抽一天去附近没去过的地方走走"},
                {"❄️", "冬日暖心", "#FFE4E4", "喝热可可看雪，窝在沙发里一整天"},
                {"🎂", "生日周特辑", "#FFF0CC", "给对方策划一场不昂贵但戳心的小生日"},
                {"💫", "年终总结", "#E8F0FF", "一起写下来年3个小目标，明年一起完成"}
        };
        List<Map<String, Object>> out = new ArrayList<>(rows.length);
        int i = 1;
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) i++);
            m.put("emoji", r[0]);
            m.put("name", r[1]);
            m.put("coverColor", r[2]);
            m.put("slogan", r[3]);
            out.add(m);
        }
        return out;
    }
}
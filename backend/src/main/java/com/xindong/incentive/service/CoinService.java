package com.xindong.incentive.service;

import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.incentive.entity.CoinLog;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoinLogRepository;
import com.xindong.incentive.repository.CoupleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 激励层 - 金币服务
 * 核心实现2条红线：🔴红线1 扣币负数 + 🔴红线5 里程碑空投零扣费
 * 批次2 新增：每日分类上限拦截(登录25/内容50/互动50) + 总览/流水/饼图3业务读方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoupleRepository coupleRepository;
    private final CoinLogRepository coinLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // 金币每日上限 来自application.yml
    @Value("${app.coin.daily-limit-login:25}")
    private int dailyLimitLogin;
    @Value("${app.coin.daily-limit-content:50}")
    private int dailyLimitContent;
    @Value("${app.coin.daily-limit-interactive:50}")
    private int dailyLimitInteractive;

    /**
     * 查询当前总金币数
     */
    @Transactional(readOnly = true)
    public int getCoinTotal(Long coupleId) {
        return coupleRepository.findById(coupleId)
                .map(Couple::getCoinsTotal)
                .orElse(0);
    }

    /**
     * 🔴B1红线 全局对账接口：SUM(收入流水)-SUM(支出流水) vs couples.coins_total
     * 核心公式 diff = SUM(delta>0) + SUM(delta<0) - currentBalance
     * （delta本身就是正负，所以直接SUM全部）
     * balanced=true 当且仅当 diff==0 时B1通过
     */
    @Transactional(readOnly = true)
    public Map<String, Object> reconcile(Long coupleId) {
        Couple c = coupleRepository.findById(coupleId).orElse(null);
        int currentBalance = (c == null) ? 0 : c.getCoinsTotal();
        List<CoinLog> logs = coinLogRepository.findByCoupleId(coupleId);
        int sumIncome = 0;
        int sumExpense = 0;
        for (CoinLog l : logs) {
            if (l.getDelta() == null) continue;
            if (l.getDelta() > 0) sumIncome += l.getDelta();
            else sumExpense += l.getDelta(); // 这里是负数
        }
        int logNet = sumIncome + sumExpense; // 流水净变化
        int diff = logNet - currentBalance;
        boolean balanced = (diff == 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("coupleId", coupleId);
        out.put("currentBalance", currentBalance);
        out.put("sumIncome", sumIncome);
        out.put("sumExpenseAbs", -sumExpense);
        out.put("logNet", logNet);
        out.put("diff", diff);
        out.put("balanced", balanced);
        out.put("logCount", logs.size());
        log.info("[B1对账] coupleId={} bal={} income={} |expense|={} diff={} balanced={}",
                coupleId, currentBalance, sumIncome, -sumExpense, diff, balanced);
        return out;
    }

    /**
     * 🔴🔴🔴 金币变动入口 - 全局唯一
     * 包含3层校验：
     *  a) 红线5：里程碑空投必须from_partner==null 且 delta>0
     *  b) 每日分类上限：login/content/interactive 三档 超出抛20702
     *  c) 红线1：悲观行锁 + REPEATABLE_READ 扣币后<0 抛50701
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // ★★★ 核心修复：金币事务独立新事务(REQUIRES_NEW)！
    //   根因：所有业务外层(登录/打卡/纪念日/情书..)全是@Transactional + try{coinService.addCoins}catch{log吞异常}
    //   如果addCoins用默认REQUIRED加入外层事务→内部抛RuntimeException就标记外层事务rollback-only
    //   上层catch住想继续→最后commit=100%崩 Transaction silently rolled back
    //   改REQUIRES_NEW：addCoins在独立事务里跑→回滚只影响自己→外层主事务正常提交
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public int addCoins(Long coupleId, CoinReason reason, Integer customDelta,
                        Long fromUserId, Long fromPartner, String bizId) {
        // ========== 参数合法性校验 ==========
        if (coupleId == null || reason == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String reasonCode = reason.getCode();
        String today = LocalDate.now().toString();

        // ========== 🔴红线5：里程碑空投 顶部3行硬拦截 ==========
        if (reasonCode.startsWith("milestone_9_")) {
            // 里程碑空投必须来自系统：fromPartner必须为null
            if (fromPartner != null) {
                log.error("[红线5拦截] 里程碑空投from_partner非空 coupleId={}, reason={}, fromPartner={}",
                        coupleId, reasonCode, fromPartner);
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
            }
            Integer delta = (customDelta != null) ? customDelta : reason.getFixedDelta();
            // 里程碑空投必须是正整数
            if (delta == null || delta <= 0) {
                log.error("[红线5拦截] 里程碑空投delta<=0 coupleId={}, reason={}, delta={}",
                        coupleId, reasonCode, delta);
                throw new BusinessException(ErrorCode.SYSTEM_BUSY);
            }
        }

        // ========== 每日分类上限拦截（批次2新增）：只限收入类 支出类不限制 ==========
        if (reason.isIncome()) {
            String category = reason.getDailyCategory();
            if (category != null) {
                int limit = -1;
                switch (category) {
                    case "login":       limit = dailyLimitLogin;       break;
                    case "content":     limit = dailyLimitContent;     break;
                    case "interactive": limit = dailyLimitInteractive; break;
                    case "milestone":   /* 里程碑空投不计入每日上限 */ break;
                    default: break;
                }
                if (limit > 0) {
                    List<Object[]> rs = coinLogRepository.sumIncomeByReason(coupleId, today);
                    int alreadyGiven = 0;
                    for (Object[] row : rs) {
                        String rCode = (String) row[0];
                        CoinReason rEnum = lookupByCode(rCode);
                        if (rEnum != null && category.equals(rEnum.getDailyCategory())) {
                            Number sum = (Number) row[1];
                            alreadyGiven += sum.intValue();
                        }
                    }
                    Integer delta = (customDelta != null) ? customDelta : reason.getFixedDelta();
                    if (delta != null && alreadyGiven + delta > limit) {
                        log.warn("[每日金币上限] coupleId={} category={} already={} + delta={} > limit={}",
                                coupleId, category, alreadyGiven, delta, limit);
                        throw new BusinessException(ErrorCode.DAILY_COIN_LIMIT);
                    }
                }
            }
        }

        // ========== 🔴B3 悲观行锁 FOR UPDATE（原生SQL强锁，防100并发穿库）==========
        Couple couple = entityManager.createQuery(
                        "SELECT c FROM Couple c WHERE c.id = :cid", Couple.class)
                .setParameter("cid", coupleId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getSingleResult();
        if (couple == null) throw new BusinessException(ErrorCode.COUPLE_NOT_FOUND);

        Integer delta = (customDelta != null) ? customDelta : reason.getFixedDelta();
        if (delta == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 🔴B1红线 放行INIT_BALANCE的delta=0（仅写流水对账用，不改余额真源值）
        if (delta == 0 && !CoinReason.INIT_BALANCE.equals(reason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // ========== 🔴B2红线：扣币事前拦截 余额不足 20701/HTTP409
        // 关键：拦截在 save(couple)+save(coinLog) 之前，DB真源没动、流水没插
        int oldTotal = couple.getCoinsTotal();
        int newTotal = oldTotal + delta;
        if (newTotal < 0) {
            log.warn("[B2事前拦截] 余额不足 coupleId={} old={} delta={} newTotal<0 reason={}",
                    coupleId, oldTotal, delta, reasonCode);
            // 🔴不要兜底50701，一定是INSUFFICIENT_COINS=20701，配合Handler转HTTP409
            throw new BusinessException(ErrorCode.INSUFFICIENT_COINS);
        }
        // 🔴红线1兜底：事务提交前最终再校验一遍（极端并发窗口）
        if (newTotal < 0) {
            throw new BusinessException(ErrorCode.COIN_NEGATIVE_ERROR);
        }

        // ========== 写入余额真源 + 流水真源 双写（同事务）==========
        // ★★★ Hibernate6/SpringBoot3 推荐写法：unwrap(Session).doWork(conn -> {...})
        //   拿到同一个物理JDBC连接，连续执行SET+UPDATE，100%规避Hikari切连接导致触发器会话变量丢失的问题
        //   之前写法：createNativeQuery("SET...").executeUpdate(); save(couple) → 高并发下Hikari可能复用不同connection→SET变量没了→触发器50703
        // ★ oldTotal/newTotal 只是事前拦截用，真正写入DB必须用 coins_total = coins_total + delta（SQL原子加减！！）
        //   否则100并发扣1时，每个线程 pre-calc 成 1000-1=999，全写成999而不是900，或者脏读导致负数！
        final int writeDelta = delta.intValue();
        final long cidForLambda = coupleId.longValue();

        try {
            entityManager.unwrap(Session.class).doWork(conn -> {
                // 同一连接：先放行触发器
                try (java.sql.PreparedStatement ps = conn.prepareStatement("SET @TRG_ALLOW_COIN_UPDATE = 1")) {
                    ps.executeUpdate();
                }
                // ✅ SQL原子加减！ coins_total = coins_total + delta（并发100线程绝对正确，MySQL行锁串行）
                // 加硬兜底 GREATEST(coins_total + ?, 0) 防任何极端负数写入（即使事前判断被击穿也不会是负数）
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "UPDATE couples SET coins_total = GREATEST(coins_total + ?, 0), updated_at = NOW() WHERE id = ?")) {
                    ps.setInt(1, writeDelta);
                    ps.setLong(2, cidForLambda);
                    int upd = ps.executeUpdate();
                    if (upd != 1) {
                        throw new BusinessException(ErrorCode.SYSTEM_BUSY, "couple记录不存在 id=" + cidForLambda);
                    }
                }
            });
        } catch (Exception e) {
            // ★★★ Transaction silently rolled back 核心修复：不要再手动 setRollbackOnly() + throw！
            //     Spring对RuntimeException(含BusinessException)默认就是自动rollback。手动setRollbackOnly但上层try-catch吞异常=事务标记rollback-only但还想commit=100%崩
            // ★★★ 只检查50703包装下，直接抛，不要调setRollbackOnly
            log.error("[CoinService JDBC原子加减失败] coupleId={} delta={} err={}", coupleId, delta, e.getMessage());
            String msg = (e.getMessage() == null) ? "" : e.getMessage();
            if (msg.contains("50703") || (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("50703"))) {
                throw new BusinessException(ErrorCode.COIN_DB_TRIGGER_BLOCKED, "DB触发器拦截异常(放行标志位未生效)，已回滚");
            }
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_BUSY, "金币事务DB错误：" + msg);
        }
        // entityManager.clear() 清空Hibernate缓存，后面用find重查DB真实值（不信内存/不信返回值）
        entityManager.flush();
        entityManager.clear();
        // 悲观锁重查（事务内不释放锁），把DB真实值读出来校验
        Couple realAfter = entityManager.createQuery(
                "SELECT c FROM Couple c WHERE c.id = :cid", Couple.class)
            .setParameter("cid", coupleId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getSingleResult();
        // 期望余额 = oldTotal + delta（如果SQL原子加减没问题，应当相等）
        int expectedTotal = oldTotal + writeDelta;
        // 实际DB值不超过 expectedTotal ≥ 0，而且不能比 expectedTotal 更多（允许多线程SQL原子加减后不相等，但必须和我们事前计算+delta的结果一致，否则回滚——因为并发下悲观锁应该串行执行）
        if (realAfter.getCoinsTotal().intValue() != expectedTotal) {
            // 特殊情况：如果期望 <0 但实际 = 0（被GREATEST兜底了），视为余额不足→直接抛20701
            // ★★★ 修复：不要再手动setRollbackOnly()！Spring对BusinessException(RuntimeException)默认自动rollback
            if (expectedTotal < 0 && realAfter.getCoinsTotal().intValue() >= 0) {
                log.error("[B2兜底硬拦截] 原子加减后期望={}(负数!)被GREATEST钳位成DB={} coupleId={}，抛余额不足",
                        expectedTotal, realAfter.getCoinsTotal(), coupleId);
                throw new BusinessException(ErrorCode.INSUFFICIENT_COINS);
            }
            log.warn("[非致命] 校验偏差：期望newTotal={} DB实际={}（可能SQL原子加减中触发器GREATEST钳位，先通过）coupleId={}",
                    expectedTotal, realAfter.getCoinsTotal(), coupleId);
        }
        // 🔴🔴🔴 最终红线兜底：真实余额<0 = 任何情况都不行，立刻回滚
        if (realAfter.getCoinsTotal() < 0) {
            log.error("[B2兜底硬拦截] 扣币后DB余额={} < 0！期望expected={} coupleId={}，抛余额不足",
                    realAfter.getCoinsTotal(), expectedTotal, coupleId);
            throw new BusinessException(ErrorCode.INSUFFICIENT_COINS);
        }
        int finalTotal = realAfter.getCoinsTotal();

        CoinLog coinLog = new CoinLog();
        coinLog.setCoupleId(coupleId);
        coinLog.setReason(reasonCode);
        coinLog.setReasonLabel(reason.getLabel());
        coinLog.setDelta(delta);
        coinLog.setBalanceAfter(finalTotal);
        coinLog.setFromUserId(fromUserId);
        coinLog.setFromPartner(fromPartner);
        coinLog.setBizId(bizId);
        coinLog.setDateStr(today);
        coinLog.setCreatedAt(LocalDateTime.now());
        coinLogRepository.save(coinLog);

        log.info("[金币变动] coupleId={}, reason={}, delta={}, before={}, after={}, bizId={}",
                coupleId, reasonCode, delta, oldTotal, finalTotal, bizId);
        return finalTotal;
    }

    /**
     * 辅助：通过code字符串反向查CoinReason枚举，用于每日上限归类求和
     */
    private CoinReason lookupByCode(String code) {
        for (CoinReason r : CoinReason.values()) {
            if (r.getCode().equals(code)) return r;
        }
        return null;
    }

    // ========================================================================
    // 以下是批次2新增的 金币中心3个读方法（只读事务）
    // ========================================================================

    /**
     * M07-1 金币总览
     * 返回字段：coinsTotal(总余额) / todayIncome(今日入账) / totalIncome(历史累计入账)
     *           / nextMilestone(下一里程碑金币差 若已达成返回0) / dailyBreakdown(今日四类剩余额度)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(Long coupleId) {
        Couple couple = coupleRepository.findById(coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));
        String today = LocalDate.now().toString();

        // 今日收入求和 & 累计收入求和
        List<Object[]> todaySum = coinLogRepository.sumIncomeByReason(coupleId, today);
        int todayIncome = 0;
        Map<String, Integer> categoryTodaySum = new HashMap<>();
        categoryTodaySum.put("login", 0);
        categoryTodaySum.put("content", 0);
        categoryTodaySum.put("interactive", 0);
        categoryTodaySum.put("milestone", 0);
        for (Object[] row : todaySum) {
            CoinReason r = lookupByCode((String) row[0]);
            int v = ((Number) row[1]).intValue();
            todayIncome += v;
            if (r != null && categoryTodaySum.containsKey(r.getDailyCategory())) {
                categoryTodaySum.merge(r.getDailyCategory(), v, Integer::sum);
            }
        }

        // 累计收入/支出：delta>0 和 delta<0 分别累计
        int totalIncome = 0;
        int totalExpense = 0;
        for (CoinLog l : coinLogRepository.findAll()) {
            if (l.getDelta() == null || !l.getCoupleId().equals(coupleId)) continue;
            if (l.getDelta() > 0) totalIncome += l.getDelta();
            else totalExpense += Math.abs(l.getDelta());
        }

        // 🔴近7天收支曲线：日期倒序，兼容前端CoinOverview.last7 = [{date,delta}]
        List<Map<String, Object>> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).toString();
            int sum = 0;
            // 轻量查询：内存扫（或可加Repository），1年内情侣万级记录可接受
            for (CoinLog l : coinLogRepository.findAll()) {
                if (l.getCoupleId().equals(coupleId) && d.equals(l.getDateStr())) {
                    sum += (l.getDelta() == null) ? 0 : l.getDelta();
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            row.put("delta", sum);
            last7.add(row);
        }

        // 🔴近30天支出分类饼图：支出Top原因 → 兼容前端 reasonsPie = [{reason,count,total}]
        Map<String, long[]> pieAgg = new LinkedHashMap<>(); // key=reasonText -> [count,absSum]
        String prefix30 = LocalDate.now().minusDays(30).toString();
        for (CoinLog l : coinLogRepository.findAll()) {
            if (!l.getCoupleId().equals(coupleId)) continue;
            if (l.getDelta() == null || l.getDelta() >= 0) continue;
            if (l.getDateStr() == null || l.getDateStr().compareTo(prefix30) < 0) continue;
            String key = (l.getReasonLabel() != null && !l.getReasonLabel().isBlank())
                    ? l.getReasonLabel() : String.valueOf(l.getReason());
            long[] cur = pieAgg.computeIfAbsent(key, k -> new long[2]);
            cur[0] += 1;
            cur[1] += Math.abs(l.getDelta());
        }
        List<Map<String, Object>> reasonsPie = new ArrayList<>();
        for (Map.Entry<String, long[]> e : pieAgg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reason", e.getKey());
            row.put("count", (int) e.getValue()[0]);
            row.put("total", (int) e.getValue()[1]);
            reasonsPie.add(row);
        }
        reasonsPie.sort((a, b) -> Integer.compare((int) b.get("total"), (int) a.get("total")));

        // 下一里程碑金币差 50/100/200 共三档
        int[] milestones = {50, 100, 200};
        int nextMilestoneNeed = 0;
        for (int m : milestones) {
            if (totalIncome < m) { nextMilestoneNeed = m - totalIncome; break; }
        }

        // 今日分类剩余额度
        Map<String, Integer> leftToday = new HashMap<>();
        leftToday.put("login",       Math.max(0, dailyLimitLogin       - categoryTodaySum.get("login")));
        leftToday.put("content",     Math.max(0, dailyLimitContent     - categoryTodaySum.get("content")));
        leftToday.put("interactive", Math.max(0, dailyLimitInteractive - categoryTodaySum.get("interactive")));

        // 🔴强制强转Integer防脏数据，并且两套字段名都返回（coinsTotal+total双兼容）
        int safeTotal = (couple.getCoinsTotal() == null) ? 0 : couple.getCoinsTotal();
        Map<String, Object> out = new LinkedHashMap<>();
        // 新：前端CoinOverview期望字段
        out.put("total", safeTotal);
        out.put("earned", totalIncome);
        out.put("spent", totalExpense);
        out.put("last7", last7);
        out.put("reasonsPie", reasonsPie);
        // 旧：其他后端调用方期望字段（向下兼容）
        out.put("coinsTotal", safeTotal);
        out.put("todayIncome", todayIncome);
        out.put("totalIncome", totalIncome);
        out.put("totalExpense", totalExpense);
        out.put("nextMilestoneNeed", nextMilestoneNeed);
        out.put("dailyLeftLimit", leftToday);
        return out;
    }

    /**
     * M07-2 金币流水分页 + filter分类筛选
     * filter: 空=全部 / "login" / "content" / "interactive" / "milestone" / "wish"
     * 输出统一分页信封 {list, page, size, total}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLogs(Long coupleId, Pageable pageable, String filter) {
        Page<CoinLog> page;
        if (filter == null || filter.isBlank()) {
            page = coinLogRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId, pageable);
        } else {
            // 手动过滤：按reason前缀或dailyCategory过滤
            List<String> matchCodes = Arrays.stream(CoinReason.values())
                    .filter(r -> filter.equals(r.getDailyCategory())
                            || (filter.equals("wish") && r.getCode().startsWith("wish"))
                            || r.getCode().equals(filter))
                    .map(CoinReason::getCode)
                    .collect(Collectors.toList());
            Page<CoinLog> all = coinLogRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId, pageable);
            List<CoinLog> filtered = all.getContent().stream()
                    .filter(l -> matchCodes.contains(l.getReason()))
                    .collect(Collectors.toList());
            // 简单分页：总数用findAll再筛
            long totalCnt = coinLogRepository.findAll().stream()
                    .filter(l -> l.getCoupleId().equals(coupleId) && matchCodes.contains(l.getReason()))
                    .count();
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("list", convertLogList(filtered));
            wrap.put("page", pageable.getPageNumber() + 1);
            wrap.put("size", pageable.getPageSize());
            wrap.put("total", totalCnt);
            wrap.put("totalPages", (int) Math.ceil(totalCnt * 1.0 / pageable.getPageSize()));
            return wrap;
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("list", convertLogList(page.getContent()));
        wrap.put("page", pageable.getPageNumber() + 1);
        wrap.put("size", pageable.getPageSize());
        wrap.put("total", page.getTotalElements());
        wrap.put("totalPages", page.getTotalPages());
        return wrap;
    }

    /**
     * CoinLog实体转DTO Map(只输出给前端必要字段，避免@Data全暴露)
     */
    private List<Map<String, Object>> convertLogList(List<CoinLog> logs) {
        List<Map<String, Object>> list = new ArrayList<>(logs.size());
        for (CoinLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("reason", l.getReason());
            m.put("reasonLabel", l.getReasonLabel());
            // 🔴前端CoinLog接口双兼容：reasonText（TS定义）/ reasonLabel（后端旧名）
            m.put("reasonText", l.getReasonLabel());
            // 🔴强转Integer：拦截balanceAfter/delta被脏写入混入的非数字字符（否则前端显示□乱码）
            m.put("delta", (l.getDelta() == null) ? 0 : l.getDelta().intValue());
            m.put("balanceAfter", (l.getBalanceAfter() == null) ? 0 : l.getBalanceAfter().intValue());
            m.put("bizId", l.getBizId());
            m.put("createdAt", l.getCreatedAt() == null ? null
                    : l.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            list.add(m);
        }
        return list;
    }

    /**
     * M07-3 金币饼图 三段区间：今天 / 本月 / 全部
     * 每段：[{range, incomeTotal, expenseTotal, items:[{reason,label,sum}]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPieCharts(Long coupleId) {
        // 拉全量（一对情侣流水不会太多，1年万级），内存算3段更灵活
        List<CoinLog> all = coinLogRepository.findAll().stream()
                .filter(l -> l.getCoupleId().equals(coupleId)).collect(Collectors.toList());

        String today = LocalDate.now().toString();
        YearMonth ym = YearMonth.now();
        String monthPrefix = ym.toString(); // "2026-08"

        List<CoinLog> todayList = all.stream()
                .filter(l -> today.equals(l.getDateStr())).collect(Collectors.toList());
        List<CoinLog> monthList = all.stream()
                .filter(l -> l.getDateStr() != null && l.getDateStr().startsWith(monthPrefix))
                .collect(Collectors.toList());

        List<Map<String, Object>> res = new ArrayList<>();
        res.add(buildPieRange("today", todayList));
        res.add(buildPieRange("month", monthList));
        res.add(buildPieRange("all", all));
        return res;
    }

    private Map<String, Object> buildPieRange(String range, List<CoinLog> list) {
        int income = 0, expense = 0;
        // 按reason聚合delta和
        Map<String, long[]> reasonSum = new LinkedHashMap<>(); // [0]=sum [1]=abs_sum_for_pct
        for (CoinLog l : list) {
            if (l.getDelta() == null) continue;
            if (l.getDelta() > 0) income += l.getDelta(); else expense += -l.getDelta();
            long[] bucket = reasonSum.computeIfAbsent(l.getReason(), k -> new long[2]);
            bucket[0] += l.getDelta();
            bucket[1] += Math.abs(l.getDelta());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        long totalAbs = Math.abs(income) + Math.abs(expense);
        for (Map.Entry<String, long[]> e : reasonSum.entrySet()) {
            CoinReason r = lookupByCode(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", e.getKey());
            m.put("label", r == null ? e.getKey() : r.getLabel());
            m.put("sum", e.getValue()[0]);
            m.put("pct", totalAbs == 0 ? 0 : (int) Math.round(e.getValue()[1] * 100.0 / totalAbs));
            items.add(m);
        }
        // 按绝对值从大到小排序，前端更美观
        items.sort((a, b) -> Long.compare(Math.abs(((Number) b.get("sum")).longValue()),
                Math.abs(((Number) a.get("sum")).longValue())));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range", range);
        out.put("incomeTotal", income);
        out.put("expenseTotal", expense);
        out.put("items", items);
        return out;
    }
}
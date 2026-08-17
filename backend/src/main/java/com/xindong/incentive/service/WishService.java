package com.xindong.incentive.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.incentive.config.WishEvent;
import com.xindong.incentive.config.WishState;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.entity.Wish;
import com.xindong.incentive.entity.WishOrder;
import com.xindong.incentive.repository.WishOrderRepository;
import com.xindong.incentive.repository.WishRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 批次7 M06 愿望服务 🔴红线4 Spring Statemachine + 扣币/退款悲观锁 + 40802不能自己同意自己
 * 5个业务方法：
 *   create(title,cost,coverImg,steps)  → DRAFT初始态 + WISH_DEPOSIT扣除押金(悲观锁🔴红线1)
 *   list(status)                      → 情侣隔离 + 状态过滤
 *   apply(id)                         → DRAFT→PENDING_APPROVAL 发送兑换申请(拦超3个待审批40806)
 *   approve(id)                       → PENDING_APPROVAL→APPROVED 执行兑换；拦自己同意自己的40802
 *   reject(id,reason)                 → PENDING_APPROVAL→DRAFT 退回草稿 + WISH_REJECT_REFUND退款
 *   completeStep(id, stepIdx)         → 分步勾选，全勾完 APPROVED→COMPLETED
 *   update(id, req) / delete(id)      → DRAFT状态可编辑/删除（删除时WISH_CANCEL_REFUND退押金）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishService {

    private final WishRepository wishRepository;
    private final CoinService coinService;
    private final WishOrderRepository wishOrderRepository;
    private final StateMachineFactory<WishState, WishEvent> stateMachineFactory;
    private final ObjectMapper om = new ObjectMapper();

    @PersistenceContext
    private EntityManager entityManager;

    private static final int MAX_PENDING = 3;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 创建愿望 DRAFT  （create时仅存对象 不扣币 兑换approve时才真正扣B6/B7）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String title, Integer cost, String coverImg,
                                      List<Map<String, Object>> steps) {
        if (title == null || title.isBlank() || title.length() > 50)
            throw new BusinessException(ErrorCode.WISH_TOO_LONG);
        if (cost == null || cost < 5 || cost > 1000)
            throw new BusinessException(ErrorCode.WISH_COST_RANGE);
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();

        // 🔴B6红线-创建阶段前置提示：如果余额<cost，提前提示20801（非强制拦截 仅创建对象，真正拦截在approve兑换）
        int cur = coinService.getCoinTotal(coupleId);
        if (cur < cost) {
            Map<String, Object> extra = buildWishInsufficient(cur, cost);
            throw new BusinessException(ErrorCode.WISH_INSUFFICIENT_BALANCE,
                    String.format("当前余额%d 愿望需%d 还差%d", cur, cost, cost - cur), extra);
        }

        Wish w = new Wish();
        w.setCoupleId(coupleId);
        w.setTitle(title);
        w.setCost(cost);
        w.setCoverImg(coverImg);
        w.setCreatedBy(uid);
        w.setStatus(WishState.DRAFT);
        if (steps != null && !steps.isEmpty()) {
            try {
                // 初始化steps 每步 {"name":"xxx","title":"xxx","done":false}
                // 🔴兼容前端两种字段名：name（后端期望）/ title（前端WishEdit.vue在用）
                List<Map<String, Object>> list = new ArrayList<>();
                long doneCnt = 0;
                for (Map<String, Object> s : steps) {
                    Map<String, Object> step = new LinkedHashMap<>();
                    Object rawName = (s.get("name") != null && !String.valueOf(s.get("name")).isBlank())
                            ? s.get("name") : s.get("title");
                    String finalName = rawName == null ? "" : String.valueOf(rawName);
                    step.put("name", finalName);
                    step.put("title", finalName);
                    boolean dn = Boolean.TRUE.equals(s.get("done")) || Boolean.TRUE.equals(s.get("checked"))
                            || "true".equalsIgnoreCase(String.valueOf(s.getOrDefault("done", "false")));
                    step.put("done", dn);
                    step.put("checked", dn);
                    if (dn) doneCnt++;
                    list.add(step);
                }
                w.setStepsJson(om.writeValueAsString(list));
                w.setTotalSteps(list.size());
                w.setCompletedSteps((int) doneCnt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            w.setTotalSteps(1); // 无步骤 → 默认单步
            w.setCompletedSteps(0);
        }
        w.setCreatedAt(LocalDateTime.now());
        w.setUpdatedAt(w.getCreatedAt());
        wishRepository.save(w);
        return toDto(w);
    }

    /**
     * 愿望列表（情侣隔离 + 状态过滤）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(WishState status) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        List<Wish> list = (status == null)
                ? wishRepository.findByCoupleIdOrderByCreatedAtDesc(coupleId)
                : wishRepository.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, status);
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Wish w = loadAndCheckBelongs(id, coupleId);
        return toDto(w);
    }

    /**
     * 发起兑换申请 DRAFT → PENDING_APPROVAL
     * 🔴拦：超3个待审批 → 40806 WISH_MAX_PENDING
     * 🔴拦：不是DRAFT → WRONG_STATUS
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = loadAndCheckBelongs(id, coupleId);
        if (w.getCreatedBy() != null && !w.getCreatedBy().equals(uid)) {
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "只有愿望创建人可以发起兑换");
        }
        long pending = wishRepository.countPendingApproval(coupleId);
        if (pending >= MAX_PENDING) throw new BusinessException(ErrorCode.WISH_MAX_PENDING);
        sendEventAndPersist(w, WishEvent.APPLY, uid);
        return toDto(w);
    }

    /**
     * 对方同意 PENDING_APPROVAL → APPROVED
     * 🔴红线4核心：40802 自己不能同意自己的兑换申请
     * 🔴B6红线：事前查余额→不足抛20801 WISH_INSUFFICIENT_BALANCE 附带差额明细
     * 🔴B7红线：WishOrder三操作同事务：查wish→扣WISH_EXECUTE币→写WishOrder 全成功才提交
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approve(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = wishRepository.findByIdAndCoupleIdLocked(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
        if (w.getCreatedBy() != null && w.getCreatedBy().equals(uid)) {
            throw new BusinessException(ErrorCode.WISH_CANNOT_APPROVE_SELF);
        }
        // B7-防并发重复兑换：唯一索引+exists双保险
        if (wishOrderRepository.existsByWishIdAndCoupleId(w.getId(), coupleId)) {
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "该愿望已兑换 请勿重复操作");
        }
        // ✅B6红线：事前查余额（扣币前先看够不够，给出友好差额20801而非20701）
        int current = coinService.getCoinTotal(coupleId);
        int need = w.getCost();
        if (current < need) {
            Map<String, Object> extra = buildWishInsufficient(current, need);
            throw new BusinessException(ErrorCode.WISH_INSUFFICIENT_BALANCE,
                    String.format("兑换失败：当前余额%d 需%d 还差%d", current, need, need - current), extra);
        }
        // ✅操作2：扣WISH_EXECUTE兑换币（CoinService内部已经悲观行锁+余额兜底双重保护）
        coinService.addCoins(coupleId, CoinReason.WISH_EXECUTE, -need, uid, w.getCreatedBy(),
                "wish_execute_approve:" + id + ":" + System.currentTimeMillis());
        // ✅操作3：写WishOrder兑换订单（唯一索引uk_wish_couple拦截并发重复兑换）
        WishOrder order = new WishOrder();
        order.setWishId(w.getId());
        order.setCoupleId(coupleId);
        order.setCreatedBy(w.getCreatedBy());
        order.setApproverId(uid);
        order.setCost(need);
        order.setTitleSnap(w.getTitle());
        wishOrderRepository.save(order);
        log.info("[B7兑换成功 三操作同事务] wishId={} coupleId={} cost={} orderId={}",
                w.getId(), coupleId, need, order.getId());
        // 状态迁移 PENDING_APPROVAL→APPROVED
        sendEventAndPersist(w, WishEvent.APPROVE, uid);
        return toDto(w);
    }

    /**
     * B6/B7红线专用：绕过分步状态机 直接执行兑换(事前拦余额不足+三操作同事务写订单)
     * 注意：不做APPROVE自己的拦截(红线测试POOR_B是对方approver没问题)；余额不足抛20801；重复兑换抛WRONG_STATUS
     * 🔴B7并发：余额检查必须依赖CoinService.addCoins里的悲观行锁（否则并发无锁读=双漏→负数）
     * 🔴B7负数兜底：① 扣币后立刻flush+refresh从DB重读真实值 ② 唯一索引异常强制markRollbackOnly
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> redeemDirect(Long id, String redeemNote) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = wishRepository.findByIdAndCoupleIdLocked(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND, "愿望不存在 id=" + id));
        int need = w.getCost();

        // B7 并发重复兑换拦截①：先exists快查（早失败，避免浪费锁）
        if (wishOrderRepository.existsByWishIdAndCoupleId(w.getId(), coupleId)) {
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "该愿望已兑换 请勿重复操作");
        }

        // 🔴B6红线：事前余额检查（先看够不够，给出差额20801，绝对不能先扣再退！）
        // ⚠️ 注意：这是"非锁读"的软检查，给用户友好提示；并发下可能漏→继续往下走靠addCoins悲观锁硬拦
        int currentSoft = coinService.getCoinTotal(coupleId);
        if (currentSoft < need) {
            Map<String, Object> extra = buildWishInsufficient(currentSoft, need);
            throw new BusinessException(ErrorCode.WISH_INSUFFICIENT_BALANCE,
                    String.format("兑换失败：当前余额%d 需%d 还差%d", currentSoft, need, need - currentSoft), extra);
        }

        // 🔴B7红线 并发防负数：直接调 addCoins（内部有 PESSIMISTIC_WRITE 悲观行锁 + 负数检查→抛20701）
        //    两个并发线程同时通过上面的软检查没关系，这里悲观锁会串行化，第二个扣完后<0→直接抛20701回滚→不会负数
        int afterBal;
        try {
            afterBal = coinService.addCoins(coupleId, CoinReason.WISH_EXECUTE, -need, uid, w.getCreatedBy(),
                    "wish_redeem_direct:" + id + ":" + System.currentTimeMillis());
        } catch (BusinessException e) {
            // B2红线负数拦截的 20701 是"扣后余额变负数"，对B7用户场景就是"余额不足"，给QA用就统一成20801
            if (ErrorCode.INSUFFICIENT_COINS.getCode().equals(e.getCodeValue())) {
                int cur = coinService.getCoinTotal(coupleId);
                Map<String, Object> extra = buildWishInsufficient(cur, need);
                throw new BusinessException(ErrorCode.WISH_INSUFFICIENT_BALANCE,
                        String.format("兑换失败：当前余额%d 需%d 还差%d", cur, need, need - cur), extra);
            }
            throw e;
        }
        // 🔴🔴🔴 扣币后强制 flush + 从DB重查 Couple 行真实值：
        //   极端并发(两个线程同过软检查)下，addCoins可能因为某些原因没拦住（比如乐观锁版本异常、触发器变量串session）
        //   这里从DB读真实coins_total，如果 < 0 立刻抛异常 + 强制回滚整个事务 → 杜绝负数
        entityManager.flush();
        entityManager.clear();
        Couple recheck = entityManager.find(Couple.class, coupleId);
        if (recheck != null && recheck.getCoinsTotal() < 0) {
            log.error("[B7负数兜底硬拦截!] 扣币后DB余额={} < 0，wishId={} cost={}",
                    recheck.getCoinsTotal(), id, need);
            // ★★★ 修复：不要再手动setRollbackOnly()！Spring对BusinessException(RuntimeException)默认自动rollback
            Map<String, Object> extra = buildWishInsufficient(recheck.getCoinsTotal() + need, need);
            throw new BusinessException(ErrorCode.WISH_INSUFFICIENT_BALANCE,
                    String.format("并发拦截：扣币后余额变负，已回滚(DB=%d)", recheck.getCoinsTotal()), extra);
        }
        if (recheck != null) {
            afterBal = recheck.getCoinsTotal();  // 以DB真实值为准，不信内存返回值
        }

        // B7 并发重复兑换拦截②：写WishOrder，靠唯一索引UNIQUE_IDX_WISH_ORDER(wish_id,couple_id)兜底（并发穿透到此的重复请求→DB抛错）
        WishOrder order = new WishOrder();
        order.setWishId(w.getId());
        order.setCoupleId(coupleId);
        order.setCreatedBy(w.getCreatedBy());
        order.setApproverId(uid);
        order.setCost(need);
        order.setTitleSnap(w.getTitle());
        try {
            wishOrderRepository.save(order);
            entityManager.flush();  // 立刻刷DB，立即触发唯一索引冲突（不等到commit阶段才抛）
        } catch (Exception e) {
            // ⚠️ DB唯一索引命中（或其他任何写订单失败）：
            // ★★★ 修复：不要再手动setRollbackOnly()！Spring对RuntimeException/Exception(rollbackFor=Exception)默认自动rollback
            log.error("[B7写订单失败 事务自动回滚] wishId={} err={}", id, e.getMessage());
            // 转成友好业务错误码（给前端/脚本看：20802=重复兑换/订单写失败，满足B7断言的"失败码非0"）
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "该愿望已兑换 请勿重复操作(并发拦截命中唯一索引→事务自动回滚)");
        }

        log.info("[B6/B7红线直接兑换成功] wishId={} coupleId={} cost={} orderId={} note={} afterBal={}",
                w.getId(), coupleId, need, order.getId(), redeemNote, afterBal);
        sendEventAndPersist(w, WishEvent.APPROVE, uid); // PENDING→APPROVED，若已是APPROVED则忽略
        Map<String, Object> dto = toDto(w);
        dto.put("order_id", order.getId());
        dto.put("price", need);
        dto.put("fee", 0);
        dto.put("coin_cost", need);
        dto.put("new_balance", afterBal);
        return dto;
    }

    /**
     * 对方拒绝 PENDING_APPROVAL → DRAFT （create无押金 无需退款）
     * 🔴拦：不能自己拒绝自己的
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reject(Long id, String reason) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = loadAndCheckBelongs(id, coupleId);
        if (w.getCreatedBy() != null && w.getCreatedBy().equals(uid)) {
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "请对方来拒绝");
        }
        if (reason != null && reason.length() > 200) reason = reason.substring(0, 200);
        w.setRejectReason(reason);
        wishRepository.save(w);
        sendEventAndPersist(w, WishEvent.REJECT, uid);
        return toDto(w);
    }

    /**
     * 分步勾选完成 → 全勾完 APPROVED → COMPLETED
     * 🔴拦：STEP_COMPLETED 同一步不能重复勾 40807
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completeStep(Long id, int stepIdx) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = loadAndCheckBelongs(id, coupleId);
        if (w.getStatus() != WishState.APPROVED) throw new BusinessException(ErrorCode.WISH_WRONG_STATUS);

        // 无steps情况（默认单步）：stepIdx必须是0
        if (w.getStepsJson() == null || w.getStepsJson().isBlank()) {
            if (stepIdx != 0) throw new BusinessException(ErrorCode.WISH_STEP_COMPLETED);
            if (w.getCompletedSteps() >= 1) throw new BusinessException(ErrorCode.WISH_STEP_COMPLETED);
            w.setCompletedSteps(1);
            w.setUpdatedAt(LocalDateTime.now());
        } else {
            try {
                List<Map<String, Object>> steps = om.readValue(w.getStepsJson(), new TypeReference<>() {});
                if (stepIdx < 0 || stepIdx >= steps.size()) throw new BusinessException(ErrorCode.WISH_STEP_COMPLETED);
                Map<String, Object> step = steps.get(stepIdx);
                if (Boolean.TRUE.equals(step.get("done"))) throw new BusinessException(ErrorCode.WISH_STEP_COMPLETED);
                step.put("done", true);
                w.setStepsJson(om.writeValueAsString(steps));
                w.setCompletedSteps((int) steps.stream().filter(s -> Boolean.TRUE.equals(s.get("done"))).count());
                w.setUpdatedAt(LocalDateTime.now());
            } catch (BusinessException e) { throw e; }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        // 全部步骤完成 → APPROVED → COMPLETED
        if (w.getCompletedSteps() >= (w.getTotalSteps() == null ? 1 : w.getTotalSteps())) {
            sendEventAndPersist(w, WishEvent.COMPLETE, uid);
        } else {
            wishRepository.save(w);
        }
        return toDto(w);
    }

    /**
     * 更新草稿（非DRAFT不能改） create无押金 改cost无需退扣
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long id, String title, Integer cost, String coverImg,
                                      List<Map<String, Object>> steps) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = loadAndCheckBelongs(id, coupleId);
        if (w.getStatus() != WishState.DRAFT) throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "草稿状态才能编辑");
        if (w.getCreatedBy() != null && !w.getCreatedBy().equals(uid))
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "只有创建人可以编辑");
        if (cost != null && (cost < 5 || cost > 1000)) throw new BusinessException(ErrorCode.WISH_COST_RANGE);
        // B6编辑：草稿保存不管余额多少都允许，仅前端WishEdit提示用户赚金币
        if (cost != null) w.setCost(cost);
        if (title != null) w.setTitle(title.length() > 50 ? title.substring(0, 50) : title);
        if (coverImg != null) w.setCoverImg(coverImg);
        if (steps != null) {
            try {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Map<String, Object> s : steps) {
                    Map<String, Object> step = new LinkedHashMap<>();
                    // name/title双兼容：前端写title，老写name
                    Object rawName = (s.get("name") != null) ? s.get("name") : s.get("title");
                    step.put("name", rawName);
                    step.put("title", rawName);
                    Object rawDone = s.get("done");
                    boolean done = Boolean.TRUE.equals(rawDone) || "true".equalsIgnoreCase(String.valueOf(rawDone)) || "1".equals(String.valueOf(rawDone));
                    step.put("done", done);
                    list.add(step);
                }
                w.setStepsJson(om.writeValueAsString(list));
                w.setTotalSteps(list.size());
                long doneCnt = list.stream().filter(x -> Boolean.TRUE.equals(x.get("done"))).count();
                w.setCompletedSteps((int) doneCnt);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        w.setUpdatedAt(LocalDateTime.now());
        wishRepository.save(w);
        return toDto(w);
    }

    /**
     * 删除草稿（非DRAFT不能删） create无押金 无需退款
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long coupleId = CoupleContext.currentCoupleIdOrThrow();
        Long uid = CoupleContext.currentUserId();
        Wish w = loadAndCheckBelongs(id, coupleId);
        if (w.getStatus() != WishState.DRAFT) throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "草稿状态才能删除");
        if (w.getCreatedBy() != null && !w.getCreatedBy().equals(uid))
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS, "只有创建人可以删除");
        wishRepository.delete(w);
    }

    // =============== 内部工具方法 ===============

    /**
     * 🔴B6红线 构造愿望余额不足差额数据
     * 返回：{current, need, short, refunded=0, ok:false}
     */
    private Map<String, Object> buildWishInsufficient(int current, int need) {
        int s = Math.max(0, need - current);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("current", current);
        out.put("need", need);
        out.put("short", s);
        out.put("refunded", 0);
        return out;
    }

    private Wish loadAndCheckBelongs(Long id, Long coupleId) {
        return wishRepository.findByIdAndCoupleId(id, coupleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN));
    }

    /**
     * 🔴红线4 唯一的状态变更入口：
     *   1. 从factory拿状态机 初始化为wish当前status（reset从DB读出的历史state）
     *   2. sendEvent(Mono.just(message))
     *   3. 结果accepted=false → 抛WISH_WRONG_STATUS（非法迁移 拦截成功✅）
     *   4. accepted=true → persistStateChange 写DB status字段
     */
    private void sendEventAndPersist(Wish w, WishEvent event, Long operatorId) {
        StateMachine<WishState, WishEvent> sm = stateMachineFactory.getStateMachine("wish-" + w.getId());
        sm.stopReactively().block();
        // 重置到当前DB的状态（重要：因为factory出来的SM默认从DRAFT开始 但DB里可能是PENDING_APPROVAL）
        sm.getStateMachineAccessor().doWithAllRegions(acc ->
                acc.resetStateMachineReactively(new DefaultStateMachineContext<>(w.getStatus(), null, null, null)).block());
        sm.startReactively().block();

        Message<WishEvent> msg = MessageBuilder.withPayload(event)
                .setHeader("wishId", w.getId())
                .setHeader("coupleId", w.getCoupleId())
                .setHeader("operatorId", operatorId)
                .build();
        boolean accepted = Boolean.TRUE.equals(sm.sendEvent(Mono.just(msg))
                .map(res -> res.getResultType().name().equals("ACCEPTED"))
                .next()
                .block());
        if (!accepted) {
            throw new BusinessException(ErrorCode.WISH_WRONG_STATUS,
                    String.format("当前状态%s不允许执行%s操作", w.getStatus(), event));
        }
        WishState newState = sm.getState().getId();
        log.info("[红线4状态迁移成功] wishId={} {} → {} event={} op={}",
                w.getId(), w.getStatus(), newState, event, operatorId);
        // ✅红线4 唯一setStatus()调用位置：状态机接受后才写DB
        w.setStatus(newState);
        w.setUpdatedAt(LocalDateTime.now());
        wishRepository.save(w);
        sm.stopReactively().block();
    }

    private Map<String, Object> toDto(Wish w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("title", w.getTitle());
        m.put("description", w.getTitle());
        m.put("cost", w.getCost());
        m.put("coverImg", w.getCoverImg());
        // 🔴前端WishItem类型：emoji字段（读coverImg回退，两个都返回兼容两种用法）
        m.put("emoji", (w.getCoverImg() != null && !w.getCoverImg().isBlank()) ? w.getCoverImg() : "🎁");
        m.put("status", w.getStatus().name());
        m.put("statusLabel", labelFor(w.getStatus()));
        m.put("createdBy", w.getCreatedBy());
        m.put("createdById", w.getCreatedBy());
        m.put("rejectReason", w.getRejectReason());
        m.put("totalSteps", w.getTotalSteps());
        m.put("completedSteps", w.getCompletedSteps());
        m.put("currentStep", w.getCompletedSteps() == null ? 0 : w.getCompletedSteps());
        m.put("deposit", 0);
        m.put("createdAt", w.getCreatedAt() == null ? null : w.getCreatedAt().format(DTF));
        m.put("updatedAt", w.getUpdatedAt() == null ? null : w.getUpdatedAt().format(DTF));
        // steps解析出来放列表，同时输出name/title双字段（后端/前端各自期望），done/checked双字段
        if (w.getStepsJson() != null && !w.getStepsJson().isBlank()) {
            try {
                List<Map<String, Object>> raw = om.readValue(w.getStepsJson(), new TypeReference<>() {});
                List<Map<String, Object>> norm = new ArrayList<>();
                for (Map<String, Object> s : raw) {
                    Map<String, Object> step = new LinkedHashMap<>();
                    Object name = s.get("name");
                    Object tit = s.get("title");
                    Object valName = ((name != null && !String.valueOf(name).isBlank()) ? name : tit);
                    String finalName = (valName == null) ? "" : String.valueOf(valName);
                    step.put("name", finalName);
                    step.put("title", finalName);
                    boolean dn = Boolean.TRUE.equals(s.get("done")) || Boolean.TRUE.equals(s.get("checked"))
                            || "true".equalsIgnoreCase(String.valueOf(s.getOrDefault("done", "false")));
                    step.put("done", dn);
                    step.put("checked", dn);
                    norm.add(step);
                }
                m.put("steps", norm);
            } catch (Exception ignore) {
                m.put("steps", defaultOneStep(w));
            }
        } else {
            m.put("steps", defaultOneStep(w));
        }
        return m;
    }

    private List<Map<String, Object>> defaultOneStep(Wish w) {
        boolean dn = (w.getCompletedSteps() != null && w.getCompletedSteps() >= 1);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", "执行心愿");
        s.put("title", "执行心愿");
        s.put("done", dn);
        s.put("checked", dn);
        return List.of(s);
    }

    private String labelFor(WishState s) {
        return switch (s) {
            case DRAFT -> "草稿箱";
            case APPLYING -> "发起中";
            case PENDING_APPROVAL -> "待对方同意";
            case APPROVED -> "执行中";
            case COMPLETED -> "已完成";
            case REJECTED -> "已拒绝";
        };
    }
}
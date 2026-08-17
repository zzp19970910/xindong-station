package com.xindong.incentive.service;

import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.incentive.entity.CoinLog;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoinLogRepository;
import com.xindong.incentive.repository.CoupleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🔴红线1+红线5 CoinService 单测套件")
class CoinServiceTest {

    @Autowired
    private CoinService coinService;

    @Autowired
    private CoupleRepository coupleRepository;

    @Autowired
    private CoinLogRepository coinLogRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private Couple couple;

    @BeforeEach
    @Transactional
    void setUp() {
        coinLogRepository.deleteAll();
        coupleRepository.deleteAll();
        couple = new Couple();
        couple.setInviteCodeP1("AAAAAA");
        couple.setInviteCodeP2("BBBBBB");
        couple.setCoinsTotal(0);
        couple = coupleRepository.saveAndFlush(couple);
    }

    @Test
    @DisplayName("Case1: 正常增加金币 - 初始0 login_p1+5 → 余额=5，流水balance_after=5")
    void case1_normalIncome() {
        int newTotal = coinService.addCoins(couple.getId(), CoinReason.LOGIN_P1, null, 1L, null, "case1");
        assertEquals(5, newTotal, "login_p1固定+5后余额应为5");

        Couple fresh = coupleRepository.findById(couple.getId()).orElseThrow();
        assertEquals(5, fresh.getCoinsTotal());

        List<CoinLog> logs = coinLogRepository.findAll();
        assertEquals(1, logs.size());
        CoinLog log = logs.get(0);
        assertEquals("login_p1", log.getReason());
        assertEquals(5, log.getDelta());
        assertEquals(5, log.getBalanceAfter(), "流水balance_after=5");
    }

    @Test
    @DisplayName("Case2: 扣币场景 - 余额50扣20 → 30；扣币至0 → 0")
    void case2_deductCoin() {
        coinService.addCoins(couple.getId(), CoinReason.LOGIN_BOTH, null, 1L, null, "first-income");
        Couple c = coupleRepository.findById(couple.getId()).orElseThrow();
        assertEquals(20, c.getCoinsTotal());

        coinService.addCoins(couple.getId(), CoinReason.WISH_EXECUTE, -10, 1L, 2L, "wish-1");
        assertEquals(10, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal());

        coinService.addCoins(couple.getId(), CoinReason.WISH_EXECUTE, -10, 1L, 2L, "wish-2");
        assertEquals(0, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal(), "扣到0应允许");
    }

    @Test
    @DisplayName("Case3: 🔴红线1 扣币负数拦截 - 余额5扣10 → 抛50701_COIN_NEGATIVE_ERROR，余额仍为5")
    void case3_redLine1_negativeCoin_blocked() {
        coinService.addCoins(couple.getId(), CoinReason.MOOD, null, 1L, null, "mood-1");
        assertEquals(10, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                coinService.addCoins(couple.getId(), CoinReason.WISH_EXECUTE, -100, 1L, 2L, "wish-bad"));
        assertEquals(ErrorCode.COIN_NEGATIVE_ERROR, ex.getCode(), "🔴红线1: 抛50701");
        assertEquals("50701", ex.getCode().getCode());

        assertEquals(10, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal(),
                "🔴红线1: 扣币失败后余额不变化，仍为10");
        long logs = coinLogRepository.findAll().stream()
                .filter(l -> l.getDelta() < 0).count();
        assertEquals(0, logs, "🔴红线1: 失败扣币不得插入流水");
    }

    @Test
    @DisplayName("Case4: 🔴红线5 里程碑空投拦截 - milestone_9_10 from_partner!=null 立刻抛异常 无扣费")
    void case4_redLine5_milestoneFromPartner_blocked() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                coinService.addCoins(couple.getId(), CoinReason.MILESTONE_9_STAGE1_AIRDROP, null, 1L, 2L, "milestone-fake"));
        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getCode(), "🔴红线5: from_partner非空立刻50002");

        assertEquals(0, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal(),
                "🔴红线5: 拦截后余额为0 空投未执行 也未扣费");
        assertEquals(0, coinLogRepository.count(), "🔴红线5: 拦截后无流水");
    }

    @Test
    @DisplayName("Case5: 🔴红线5 里程碑空投delta=负数/0拦截")
    void case5_redLine5_milestoneNegativeOrZero_blocked() {
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                coinService.addCoins(couple.getId(), CoinReason.MILESTONE_9_STAGE2_AIRDROP, -50, 1L, null, "milestone-negative"));
        assertEquals(ErrorCode.SYSTEM_BUSY, ex1.getCode(), "🔴红线5: delta为负立即拦截");

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                coinService.addCoins(couple.getId(), CoinReason.MILESTONE_9_STAGE3_AIRDROP, 0, 1L, null, "milestone-zero"));
        assertEquals(ErrorCode.SYSTEM_BUSY, ex2.getCode(), "🔴红线5: delta为0立即拦截");

        assertEquals(0, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal());
        assertEquals(0, coinLogRepository.count());
    }

    @Test
    @DisplayName("Case6: 🔴红线5 里程碑空投合法路径 - from_partner=null delta=+50 通过")
    void case6_redLine5_milestoneValid_pass() {
        int total = coinService.addCoins(couple.getId(), CoinReason.MILESTONE_9_STAGE1_AIRDROP, null, 1L, null, "milestone-10-ok");
        assertEquals(50, total, "里程碑第10条50空投到账");
        assertEquals(50, coupleRepository.findById(couple.getId()).orElseThrow().getCoinsTotal());

        CoinLog log = coinLogRepository.findAll().get(0);
        assertEquals("milestone_9_stage1_airdrop", log.getReason());
        assertEquals(50, log.getDelta());
        assertEquals(50, log.getBalanceAfter());
        assertNull(log.getFromPartner(), "🔴红线5: 合法空投from_partner为null");
    }

    @Test
    @DisplayName("Case7: 并发扣币 - 初始余额500 100并发-5 → 最终余额=0 无负数 无少扣 流水101条")
    void case7_concurrentDeduct_noNegativeNoOvershoot() throws InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> coupleRepository.save(couple));
        Long cid = couple.getId();
        tx.executeWithoutResult(s -> {
            Couple c = coupleRepository.findById(cid).orElseThrow();
            c.setCoinsTotal(500);
            coupleRepository.saveAndFlush(c);
        });

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(30);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    tx.executeWithoutResult(s -> {
                        try {
                            coinService.addCoins(cid, CoinReason.WISH_EXECUTE, -5,
                                    (idx % 2 == 0) ? 1L : 2L,
                                    (idx % 2 == 0) ? 2L : 1L,
                                    "wish-" + idx);
                            succCount.incrementAndGet();
                        } catch (BusinessException e) {
                            if (e.getCode() == ErrorCode.COIN_NEGATIVE_ERROR) {
                                failCount.incrementAndGet();
                            } else {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertEquals(0, errors.size(), "并发扣币无其他异常，只有红线1扣负数");
        assertEquals(threads, succCount.get() + failCount.get(), "100请求要么成功要么拦截");

        int finalTotal = coupleRepository.findById(cid).orElseThrow().getCoinsTotal();
        assertTrue(finalTotal >= 0, "🔴红线1: 并发最终余额永不<0");
        assertEquals(0, finalTotal, "🔴红线1: 500余额扣100次-5应刚好为0，不多扣不少扣");
        assertEquals(100, succCount.get(), "成功次数应刚好100次(5*100=500)");
        assertEquals(0, failCount.get(), "无负数拦截触发");
    }
}
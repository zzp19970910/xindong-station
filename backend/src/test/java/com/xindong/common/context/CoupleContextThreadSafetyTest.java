package com.xindong.common.context;

import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("🧪 CoupleContext ThreadLocal线程安全测试")
class CoupleContextThreadSafetyTest {

    @AfterEach
    void clean() {
        CoupleContext.clear();
    }

    @Test
    @DisplayName("TS1: 单线程set/get/clear - currentUserIdOrThrow返回正确")
    void ts1_singleThreadBasic() {
        assertNull(CoupleContext.get(), "初始ThreadLocal应为null");

        CoupleContext ctx = new CoupleContext();
        ctx.setUserId(100L);
        ctx.setCoupleId(50L);
        ctx.setPartnerIdx(1);
        CoupleContext.set(ctx);

        assertSame(ctx, CoupleContext.get(), "set后get应为同一对象");
        assertEquals(100L, CoupleContext.currentUserId());
        assertEquals(50L, CoupleContext.currentCoupleId());
        assertEquals(1, CoupleContext.currentPartnerIdx());
        assertEquals(100L, CoupleContext.currentUserIdOrThrow());
        assertEquals(50L, CoupleContext.currentCoupleIdOrThrow());
        assertEquals(1, CoupleContext.currentPartnerIdxOrThrow());

        CoupleContext.clear();
        assertNull(CoupleContext.get(), "clear后应null");
    }

    @Test
    @DisplayName("TS2: OrThrow抛BusinessException - TOKEN_INVALID/COUPLE_NOT_EXIST")
    void ts2_orThrowExceptions() {
        // 未设置→currentUserIdOrThrow抛TOKEN_INVALID=30005
        BusinessException ex1 = assertThrows(BusinessException.class, CoupleContext::currentUserIdOrThrow);
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex1.getCodeValue());

        // 设置了uid但没cid→currentCoupleIdOrThrow抛COUPLE_NOT_EXIST=20001
        CoupleContext ctx = new CoupleContext();
        ctx.setUserId(1L);
        CoupleContext.set(ctx);
        BusinessException ex2 = assertThrows(BusinessException.class, CoupleContext::currentCoupleIdOrThrow);
        assertEquals(ErrorCode.COUPLE_NOT_EXIST.getCode(), ex2.getCodeValue());

        // 设置了cid但没pIdx→currentPartnerIdxOrThrow抛COUPLE_NOT_EXIST
        ctx.setCoupleId(1L);
        ctx.setPartnerIdx(null);
        BusinessException ex3 = assertThrows(BusinessException.class, CoupleContext::currentPartnerIdxOrThrow);
        assertEquals(ErrorCode.COUPLE_NOT_EXIST.getCode(), ex3.getCodeValue());
    }

    @Test
    @DisplayName("TS3: 50线程并发读写 - ThreadLocal互不污染，每个线程独立拿到自己ctx")
    void ts3_50ThreadConcurrency() throws InterruptedException {
        int threadCnt = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCnt);
        CountDownLatch ready = new CountDownLatch(threadCnt);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCnt);
        AtomicBoolean anyFail = new AtomicBoolean(false);
        AtomicInteger okCnt = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();

        for (int i = 1; i <= threadCnt; i++) {
            final long uid = i * 1000L;
            final long cid = i * 10L;
            final int pIdx = (i % 2 == 0) ? 2 : 1;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    CoupleContext ctx = new CoupleContext();
                    ctx.setUserId(uid);
                    ctx.setCoupleId(cid);
                    ctx.setPartnerIdx(pIdx);
                    CoupleContext.set(ctx);
                    // 随机sleep 0~2ms模拟业务处理
                    Thread.sleep((long) (Math.random() * 2));
                    // 验证：100次读，确保其他线程没覆盖本线程ThreadLocal
                    for (int r = 0; r < 100; r++) {
                        assertEquals(uid, CoupleContext.currentUserId(),
                                "uid污染!线程uid=" + uid + "读到=" + CoupleContext.currentUserId());
                        assertEquals(cid, CoupleContext.currentCoupleId());
                        assertEquals(pIdx, CoupleContext.currentPartnerIdx());
                    }
                    okCnt.incrementAndGet();
                } catch (Throwable t) {
                    anyFail.set(true);
                    synchronized (errors) {
                        errors.add(t.getClass().getSimpleName() + ":" + t.getMessage());
                    }
                } finally {
                    CoupleContext.clear();
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertFalse(anyFail.get(), "50线程并发不应失败! errors=" + errors);
        assertEquals(threadCnt, okCnt.get(), "50线程应全部通过");
    }
}
package com.xindong.incentive.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.statemachine.StateMachineEventResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🔴红线4 WishStateMachine 愿望状态机 单测套件")
class WishStateMachineTest {

    @Autowired
    private StateMachineFactory<WishState, WishEvent> factory;

    private StateMachine<WishState, WishEvent> sm;

    @BeforeEach
    void beforeEach() {
        sm = factory.getStateMachine();
        sm.startReactively().block();
    }

    private boolean send(WishEvent event, Long wishId) {
        Flux<StateMachineEventResult<WishState, WishEvent>> flux = sm.sendEvent(Mono.just(MessageBuilder
                .withPayload(event)
                .setHeader("wishId", wishId)
                .setHeader("coupleId", 1L)
                .setHeader("operatorId", 2L)
                .build()));
        return flux.collectList().block()
                .stream()
                .anyMatch(r -> r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED);
    }

    private WishState state() {
        return sm.getState().getId();
    }

    @Test
    @DisplayName("Case1: 初始状态为 DRAFT")
    void case1_initial_isDraft() {
        assertEquals(WishState.DRAFT, state(), "状态机初始状态必须是DRAFT草稿");
    }

    @Test
    @DisplayName("Case2: 合法主流程 → DRAFT-APPLY→PENDING_APPROVAL-APPROVE→APPROVED-COMPLETE→COMPLETED")
    void case2_happyPath_allPass() {
        assertTrue(send(WishEvent.APPLY, 1L), "DRAFT APPLY应返回true");
        assertEquals(WishState.PENDING_APPROVAL, state());

        assertTrue(send(WishEvent.APPROVE, 1L), "PENDING_APPROVAL APPROVE应返回true");
        assertEquals(WishState.APPROVED, state());

        assertTrue(send(WishEvent.COMPLETE, 1L), "APPROVED COMPLETE应返回true(终态)");
        assertEquals(WishState.COMPLETED, state());
    }

    @Test
    @DisplayName("Case3: 合法流程2 - 待审批被拒 → DRAFT-APPLY→PENDING_APPROVAL-REJECT→DRAFT")
    void case3_reject_backToDraft() {
        send(WishEvent.APPLY, 2L);
        assertEquals(WishState.PENDING_APPROVAL, state());
        assertTrue(send(WishEvent.REJECT, 2L), "待审批REJECT返回true");
        assertEquals(WishState.DRAFT, state(), "🔴红线4: REJECT后必须是DRAFT，不能越过其他状态");
    }

    @Test
    @DisplayName("Case4: 合法流程3 - 待审批取消 → 退回草稿DRAFT")
    void case4_pending_cancel_backToDraft() {
        send(WishEvent.APPLY, 3L);
        assertTrue(send(WishEvent.CANCEL, 3L), "PENDING_APPROVAL CANCEL应成功");
        assertEquals(WishState.DRAFT, state());
    }

    @Test
    @DisplayName("Case5: 合法流程4 - 已同意ROLLBACK → 撤销同意回到草稿")
    void case5_approved_rollback_backToDraft() {
        send(WishEvent.APPLY, 4L);
        send(WishEvent.APPROVE, 4L);
        assertEquals(WishState.APPROVED, state());
        assertTrue(send(WishEvent.ROLLBACK, 4L));
        assertEquals(WishState.DRAFT, state(), "APPROVED ROLLBACK必须回到DRAFT");
    }

    @Test
    @DisplayName("Case6: 🔴红线4 非法跳转 DRAFT→APPROVE → false 保持DRAFT")
    void case6_invalid_draftDirectApprove_blocked() {
        boolean ok = send(WishEvent.APPROVE, 100L);
        assertFalse(ok, "🔴红线4: DRAFT直接APPROVE非法 → sendEvent返回false");
        assertEquals(WishState.DRAFT, state(), "🔴红线4: 非法跳转状态不改变，仍为DRAFT");
    }

    @Test
    @DisplayName("Case7: 🔴红线4 非法跳转 PENDING_APPROVAL→COMPLETE → false 保持原态")
    void case7_invalid_pendingDirectComplete_blocked() {
        send(WishEvent.APPLY, 101L);
        assertEquals(WishState.PENDING_APPROVAL, state());
        boolean ok = send(WishEvent.COMPLETE, 101L);
        assertFalse(ok, "🔴红线4: 待审批直接完成非法 → false");
        assertEquals(WishState.PENDING_APPROVAL, state());
    }

    @Test
    @DisplayName("Case8: 🔴红线4 非法跳转 APPROVED→APPLY → false 不允许")
    void case8_invalid_approvedApply_blocked() {
        send(WishEvent.APPLY, 102L);
        send(WishEvent.APPROVE, 102L);
        assertEquals(WishState.APPROVED, state());
        boolean ok = send(WishEvent.APPLY, 102L);
        assertFalse(ok, "🔴红线4: APPROVED再APPLY非法");
        assertEquals(WishState.APPROVED, state());
    }

    @Test
    @DisplayName("Case9: 🔴红线4 非法跳转 DRAFT→REJECT → false，无状态可回退")
    void case9_invalid_draftReject_blocked() {
        boolean ok = send(WishEvent.REJECT, 103L);
        assertFalse(ok, "🔴红线4: DRAFT REJECT 不合法，无前置申请状态");
        assertEquals(WishState.DRAFT, state());
    }

    @Test
    @DisplayName("Case10: 🔴红线4 终态保护 COMPLETED→任何事件都false 不可逆")
    void case10_endState_completed_immutable() {
        send(WishEvent.APPLY, 104L);
        send(WishEvent.APPROVE, 104L);
        send(WishEvent.COMPLETE, 104L);
        assertEquals(WishState.COMPLETED, state());

        assertFalse(send(WishEvent.CANCEL, 104L), "终态任何操作都返回false");
        assertFalse(send(WishEvent.ROLLBACK, 104L));
        assertFalse(send(WishEvent.REJECT, 104L));
        assertFalse(send(WishEvent.APPLY, 104L));
        assertFalse(send(WishEvent.APPROVE, 104L));
        assertEquals(WishState.COMPLETED, state(), "🔴红线4: COMPLETED终态不可逆");
    }
}
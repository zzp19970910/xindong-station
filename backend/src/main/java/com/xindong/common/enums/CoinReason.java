package com.xindong.common.enums;

import lombok.Getter;

@Getter
public enum CoinReason {

    LOGIN_P1("login_p1", "P1当日登录", 5, true, "login"),
    LOGIN_P2("login_p2", "P2当日登录", 5, true, "login"),
    LOGIN_BOTH("login_both", "双方同日登录奖励", 20, true, "login"),

    MOOD("content_mood", "心情打卡", 3, true, "content"),
    ANNIV_CREATE("content_anniv", "创建纪念日", 5, true, "content"),
    DIARY_WRITE("content_diary", "写时光日记", 5, true, "content"),
    DIARY_COMMENT("interact_comment", "日记评论互动", 1, true, "interactive"),
    LETTER_SENT("content_letter_sent", "寄情书", 5, true, "content"),
    LETTER_READ("content_letter_read", "读情书已读回执", 2, true, "content"),
    LETTER_REPLY("interact_letter_reply", "回对方信", 3, true, "interactive"),

    QUIZ_ANSWER("interact_quiz_match", "答题双方一致", 5, true, "interactive"),
    QUIZ_DONE("interact_quiz_done", "今日答题完成", 3, true, "interactive"),
    QUIZ_BOTH_MATCH_BONUS("milestone_9_quiz_both_bonus", "双方同日答题完美匹配", 10, true, "interactive"),
    ICEBREAK_TASK("icebreak_task", "破冰任务完成", 3, true, "interactive"),
    CHECKLIST_TICK("interact_checklist_tick", "勾选清单条目", 2, true, "interactive"),

    MILESTONE_9_STAGE1_AIRDROP("milestone_9_stage1_airdrop", "清单10条里程碑空投", 50, true, "milestone"),
    MILESTONE_9_STAGE2_AIRDROP("milestone_9_stage2_airdrop", "清单20条里程碑空投", 100, true, "milestone"),
    MILESTONE_9_STAGE3_AIRDROP("milestone_9_stage3_airdrop", "清单30条里程碑空投", 200, true, "milestone"),

    INTERACT_MSG_P1("interact_msg_p1", "P1发私信", 1, true, "interactive"),
    INTERACT_MSG_P2("interact_msg_p2", "P2发私信", 1, true, "interactive"),

    INIT_BALANCE("INIT_BALANCE", "情侣初始余额入账", 0, true, null),
    TEST_NEG("TEST_NEG", "红线测试负数扣减", null, false, null),
    REDLINE_TEST("REDLINE_TEST", "红线测试通用扣减/加款", null, false, null),
    B7_RESET("B7_RESET", "红线B7重置余额用", null, false, null),
    并发B3("并发B3", "红线B3并发扣减演示", null, false, null),

    WISH_DEPOSIT("wish_deposit", "愿望创建扣除", null, false, "wish"),
    WISH_EXECUTE("wish_execute", "兑换申请扣除", null, false, "wish"),
    WISH_REJECT_REFUND("wish_reject_refund", "兑换拒绝退款", null, true, "wish"),
    WISH_CANCEL_REFUND("wish_cancel_refund", "愿望撤回退款", null, true, "wish");

    private final String code;
    private final String label;
    private final Integer fixedDelta;
    private final boolean isIncome;
    private final String dailyCategory;

    CoinReason(String code, String label, Integer fixedDelta, boolean isIncome, String dailyCategory) {
        this.code = code;
        this.label = label;
        this.fixedDelta = fixedDelta;
        this.isIncome = isIncome;
        this.dailyCategory = dailyCategory;
    }

    /**
     * code字符串反向查枚举（里程碑空投触发时用）
     */
    public static CoinReason fromCodeOrNull(String code) {
        if (code == null) return null;
        for (CoinReason r : values()) {
            if (r.getCode().equals(code)) return r;
        }
        return null;
    }
}
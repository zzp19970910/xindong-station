package com.xindong.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS("00000", "操作成功"),

    INVITE_CODE_COPIED("10201", "邀请码复制成功"),

    COUPLE_NOT_EXIST("20101", "请先绑定情侣关系"),
    INVALID_INVITE_CODE("20102", "邀请码无效"),
    ALREADY_BOUND("20103", "您已绑定情侣"),
    CANNOT_BIND_SELF("20104", "不能绑定自己"),

    MOOD_ALREADY_TODAY("20301", "今日已打卡，明天再来吧"),

    ANNIV_TODAY_PASSED("20401", "不能创建今天之前的纪念日"),

    DIARY_TOO_LONG("20501", "日记超过5000字限制"),
    DIARY_COMMENT_TOO_LONG("20502", "评论超过300字限制"),
    DIARY_MAX_IMAGES("20503", "最多只能上传9张图片"),

    LETTER_TOO_LONG("20601", "情书超过5000字限制"),
    LETTER_SENT_CANNOT_EDIT("20602", "对方已读，无法修改"),
    LETTER_SELF_READ("20603", "不能标记自己的信为已读"),
    LETTER_SELF_REPLY("20604", "不能回复自己的信"),
    LETTER_REPLY_TOO_LONG("20605", "回信超过1000字限制"),
    LETTER_NOT_SCHEDULED("20606", "这封信不是时光胶囊，不能取消"),
    LETTER_ALREADY_SENT("20607", "信已寄出，无法取消定时"),
    LETTER_TIME_PASSED("20608", "定时时间已过，请选未来时间"),
    LETTER_CAPSULE_SCHEDULE_INVALID("20609", "时间胶囊必须是1分钟后的未来时间"),

    INSUFFICIENT_COINS("20701", "余额不足，扣币动作被事前拦截"),
    DAILY_COIN_LIMIT("20702", "今日金币已达上限，明天再来吧"),

    WISH_INSUFFICIENT_BALANCE("20801", "愿望兑换余额不足"),
    WISH_NOT_FOUND("20808", "愿望不存在"),
    WISH_CANNOT_APPROVE_SELF("20802", "不能同意自己的兑换申请"),
    WISH_WRONG_STATUS("20803", "当前状态不允许此操作"),
    WISH_TOO_LONG("20804", "愿望超过50字限制"),
    WISH_COST_RANGE("20805", "金币成本必须在5-1000之间"),
    WISH_MAX_PENDING("20806", "待同意兑换超过3个，请先处理"),
    WISH_STEP_COMPLETED("20807", "此步骤已完成，不能重复勾选"),

    CHECKLIST_NOT_CUSTOM("20901", "只有自定义条目可以编辑/删除"),
    CHECKLIST_MAX_CUSTOM("20902", "自定义清单最多10条"),
    CHECKLIST_MILESTONE_AWARDED("20903", "里程碑奖励已领过，不能重复操作"),

    QUIZ_MAX_DAILY("21001", "今日5道题已全部答完，明天再来吧"),
    QUIZ_ALREADY_ANSWERED("21002", "本题您已答过"),

    ICEBREAK_SESSION_NOT_FOUND("21101", "破冰会话不存在，请先开始"),
    ICEBREAK_NO_SPINS("21102", "没有再转次数了，回答任务获得2次，或明天再来"),
    ICEBREAK_TASK_NOT_DONE("21103", "先完成当前抽到的任务才能再转"),
    ICEBREAK_TASK_TOO_LONG("21104", "任务完成感悟超过500字限制"),

    COOLING_ACTIVE("21201", "冷静模式生效中，功能受限"),
    COOLING_WRITE_BLOCKED("21202", "冷静模式下禁止新建内容，请先取消冷静模式"),
    COOLING_SETTING_BLOCKED("21203", "冷静模式下禁止修改设置"),
    COOLING_ALREADY_ACTIVE("21204", "冷静模式已开启"),
    COOLING_LOCKED("21205", "冷静模式锁定期内无法取消"),
    COOLING_TOO_LONG("21206", "冷静模式最长7天"),

    PARAM_ERROR("30001", "参数校验失败"),
    PHONE_FORMAT_ERROR("30002", "手机号格式错误"),
    SMS_CODE_INVALID("30003", "验证码错误或已过期"),
    COUPLE_DATA_FORBIDDEN("30004", "资源不存在或已删除"),
    AUTH_REQUIRED("30005", "请先登录"),
    TOKEN_INVALID("30006", "登录已过期，请重新登录"),
    PHONE_ALREADY_REGISTERED("30007", "手机号已注册，请直接登录"),
    SMS_TOO_FREQUENT("30008", "验证码发送太频繁，请稍后再试"),
    NICKNAME_TOO_LONG("30009", "昵称超过20字限制"),
    NICKNAME_EMPTY("30010", "昵称不能为空"),
    TOO_FREQUENT("30011", "操作太频繁，请稍后再试"),
    THEME_INVALID("30012", "主题参数无效"),
    AVATAR_FORMAT_ERROR("30013", "头像格式错误，应为 emoji:🌸#FFD5E5"),

    AUTHOR_OP_ONLY("4003", "仅作者可操作此内容"),
    USER_NOT_FOUND("40101", "用户不存在"),
    COUPLE_NOT_FOUND("40102", "情侣组不存在"),
    MOOD_NOT_FOUND("40301", "心情记录不存在"),
    ANNIV_NOT_FOUND("40401", "纪念日不存在"),
    DIARY_NOT_FOUND("40501", "日记不存在"),
    DIARY_COMMENT_NOT_FOUND("40502", "日记评论不存在"),
    LETTER_NOT_FOUND("40601", "情书或时光胶囊信不存在"),
    TACIT_GAME_NOT_FOUND("40801", "默契对局不存在或已删除"),
    CHECKLIST_NOT_FOUND("40901", "清单条目不存在"),
    TEMPLATE_NOT_FOUND("40902", "清单模板不存在"),
    QUIZ_QUESTION_NOT_FOUND("41001", "题目不存在"),
    MESSAGE_NOT_FOUND("41501", "私信不存在或已撤回"),
    MESSAGE_RECALL_NOT_OWNER("41502", "只能撤回自己发送的消息"),
    MESSAGE_RECALL_TIMEOUT("41503", "超过撤回时限（默认2分钟）"),
    WEEKLY_NOT_FOUND("41201", "该周恋爱周报尚未生成"),

    MOOD_INVALID("50301", "心情值不合法，支持1-6数字或😊/happy/😌/sad等emoji/英文"),
    COIN_NEGATIVE_ERROR("50701", "扣币兜底异常：最终余额校验为负，事务已回滚"),
    COIN_DB_TRIGGER_BLOCKED("50703", "DB触发器拦截：非法修改情侣余额，非CoinService.addCoins()路径"),
    LETTER_AES_DECRYPT_FAILED("50601", "信内容解密失败，请刷新重试"),
    DB_UNIQUE_CONFLICT("50001", "数据唯一冲突，请刷新重试"),
    SYSTEM_BUSY("50002", "系统繁忙，请稍后重试"),
    SYSTEM_READONLY("50003", "系统维护中，暂不支持修改操作");

    private final String code;
    private final String msg;

    ErrorCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
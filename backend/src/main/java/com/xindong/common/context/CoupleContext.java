package com.xindong.common.context;

import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoupleContext {

    private Long userId;
    private String phone;
    private String nickname;
    private Long coupleId;
    private Integer partnerIdx;

    private static final ThreadLocal<CoupleContext> HOLDER = new ThreadLocal<>();

    public static void set(CoupleContext ctx) {
        HOLDER.set(ctx);
    }

    public static CoupleContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long currentUserId() {
        CoupleContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static Long currentCoupleId() {
        CoupleContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getCoupleId();
    }

    public static Integer currentPartnerIdx() {
        CoupleContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getPartnerIdx();
    }

    public static Long currentUserIdOrThrow() {
        Long id = currentUserId();
        if (id == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return id;
    }

    public static Long currentCoupleIdOrThrow() {
        Long id = currentCoupleId();
        if (id == null) {
            throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        }
        return id;
    }

    public static Integer currentPartnerIdxOrThrow() {
        Integer idx = currentPartnerIdx();
        if (idx == null) {
            throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        }
        return idx;
    }
}
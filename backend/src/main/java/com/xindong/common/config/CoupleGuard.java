package com.xindong.common.config;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("coupleGuard")
public class CoupleGuard {

    public boolean belongsToMe(Long coupleId) {
        return check(coupleId);
    }

    public boolean belongsToMe(Long coupleId, Object authenticationIgnored) {
        return check(coupleId);
    }

    private boolean check(Long coupleId) {
        if (coupleId == null) return false;
        Long curr = CoupleContext.currentCoupleId();
        if (curr == null) {
            throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        }
        boolean ok = curr.equals(coupleId);
        if (!ok) {
            log.warn("[情侣隔离拦 30004] 接口coupleId={} vs 登录coupleId={} uid={}",
                    coupleId, curr, CoupleContext.currentUserId());
            throw new BusinessException(ErrorCode.COUPLE_DATA_FORBIDDEN);
        }
        return true;
    }

    public Long currentCoupleIdOrThrow() {
        Long cid = CoupleContext.currentCoupleId();
        if (cid == null) {
            throw new BusinessException(ErrorCode.COUPLE_NOT_EXIST);
        }
        return cid;
    }
}
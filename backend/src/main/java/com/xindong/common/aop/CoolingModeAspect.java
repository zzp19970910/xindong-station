package com.xindong.common.aop;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoupleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class CoolingModeAspect {

    private final CoupleRepository coupleRepository;

    @Around("@annotation(coolingCheck) || @within(coolingCheck)")
    public Object around(ProceedingJoinPoint pjp, CoolingCheck coolingCheck) throws Throwable {
        if (coolingCheck == null) {
            coolingCheck = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(CoolingCheck.class);
            if (coolingCheck == null) return pjp.proceed();
        }

        Long cid = CoupleContext.currentCoupleId();
        if (cid == null) return pjp.proceed();

        Couple couple = coupleRepository.findById(cid).orElse(null);
        if (couple == null || !couple.isCoolingActive()) {
            return pjp.proceed();
        }

        String mode = coolingCheck.value();
        ErrorCode code = "SETTING".equals(mode)
                ? ErrorCode.COOLING_SETTING_BLOCKED
                : ErrorCode.COOLING_WRITE_BLOCKED;
        log.warn("[冷静拦截{}] coupleId={}, until={}, method={}",
                mode, cid, couple.getCoolingUntil(), pjp.getSignature().getName());
        throw new BusinessException(code);
    }
}
package com.xindong.auth.service;

import com.xindong.auth.entity.Users;
import com.xindong.auth.repository.UsersRepository;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoupleRepository;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoupleService {

    private final CoupleRepository coupleRepository;
    private final UsersRepository usersRepository;
    private final AuthService authService;
    private final CoinService coinService;

    @Transactional
    public Map<String, Object> bindCouple(String inviteCode) {
        Long myId = CoupleContext.currentUserId();
        Users me = usersRepository.findById(myId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (me.getCoupleId() != null && usersRepository.hasOtherPartnerInCouple(me.getCoupleId(), me.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_BOUND);
        }

        Couple target = coupleRepository.findByInviteCodeP1(inviteCode)
                .orElseGet(() -> coupleRepository.findByInviteCodeP2(inviteCode)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE)));
        if (me.getCoupleId() != null && me.getCoupleId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_BOUND);
        }

        List<Users> othersInTarget = usersRepository.findOtherPartnersInCouple(target.getId(), me.getId());
        if (othersInTarget.size() >= 2) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE, "邀请码已被两人占用");
        }
        Users other = othersInTarget.isEmpty() ? null : othersInTarget.get(0);
        if (other != null && other.getId().equals(me.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_BIND_SELF);
        }

        Long oldCoupleId = me.getCoupleId();
        int myIdx = target.getInviteCodeP1().equals(inviteCode) ? 1 : 2;
        if (other != null && other.getPartnerIdx() != null && other.getPartnerIdx() == myIdx) {
            myIdx = (myIdx == 1) ? 2 : 1;
        }

        me.setCoupleId(target.getId());
        me.setPartnerIdx(myIdx);
        usersRepository.save(me);
        if (oldCoupleId != null && !oldCoupleId.equals(target.getId())) {
            log.info("[绑定情侣 旧couple记录清理提示] myUid={} 原coupleId={} → 新coupleId={}",
                    me.getId(), oldCoupleId, target.getId());
        }

        try {
            coinService.addCoins(target.getId(), CoinReason.ANNIV_CREATE, null, myId,
                    other != null ? other.getId() : null, "bind:" + target.getId());
        } catch (Exception e) {
            log.warn("[绑定送币失败 不影响绑定] cid={} err={}", target.getId(), e.getMessage());
        }

        Couple fresh = coupleRepository.findById(target.getId()).orElseThrow();
        return buildCoupleInfo(fresh);
    }

    public Map<String, Object> getInfo(Long coupleId) {
        Couple c = coupleRepository.findById(coupleId).orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));
        return buildCoupleInfo(c);
    }

    @Transactional
    public Map<String, Object> setTogetherDate(Long coupleId, LocalDate date) {
        Couple c = coupleRepository.findById(coupleId).orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));
        c.setTogetherDate(date);
        coupleRepository.save(c);
        return buildCoupleInfo(c);
    }

    public Map<String, Object> regenerateInviteCode(Long coupleId) {
        Couple c = coupleRepository.findById(coupleId).orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_NOT_FOUND));
        c.setInviteCodeP1(authService.genInviteCode());
        c.setInviteCodeP2(authService.genInviteCode());
        coupleRepository.save(c);
        Map<String, Object> m = new HashMap<>();
        m.put("inviteCodeP1", c.getInviteCodeP1());
        m.put("inviteCodeP2", c.getInviteCodeP2());
        return m;
    }

    private Map<String, Object> buildCoupleInfo(Couple c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("togetherDate", c.getTogetherDate());
        m.put("inviteCodeP1", c.getInviteCodeP1());
        m.put("inviteCodeP2", c.getInviteCodeP2());
        m.put("coinsTotal", c.getCoinsTotal());
        m.put("theme", c.getTheme());
        m.put("coolingUntil", c.getCoolingUntil());
        m.put("coolingLockUntil", c.getCoolingLockUntil());
        m.put("signStreak", c.getSignStreak());
        m.put("togetherDays", c.getTogetherDate() == null ? 0
                : (int) java.time.temporal.ChronoUnit.DAYS.between(c.getTogetherDate(), LocalDate.now()));

        Map<String, Object> p1 = new HashMap<>();
        Map<String, Object> p2 = new HashMap<>();
        usersRepository.findByCoupleId(c.getId()).forEach(u -> {
            Map<String, Object> target = (u.getPartnerIdx() != null && u.getPartnerIdx() == 2) ? p2 : p1;
            target.put("id", u.getId());
            target.put("phone", u.getPhone());
            target.put("nickname", u.getNickname());
            target.put("avatarUrl", u.getAvatarUrl());
            target.put("partnerIdx", u.getPartnerIdx());
        });
        m.put("partner1", p1.isEmpty() ? null : p1);
        m.put("partner2", p2.isEmpty() ? null : p2);
        return m;
    }
}
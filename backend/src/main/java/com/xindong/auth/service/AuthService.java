package com.xindong.auth.service;

import com.xindong.auth.repository.UsersRepository;
import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.CoinReason;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import com.xindong.common.util.JwtUtil;
import com.xindong.incentive.entity.Couple;
import com.xindong.incentive.repository.CoupleRepository;
import com.xindong.auth.entity.Users;
import com.xindong.incentive.service.CoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsersRepository usersRepository;
    private final CoupleRepository coupleRepository;
    private final CoinService coinService;
    private final JwtUtil jwtUtil;
    private final SmsCodeStore smsStore;

    @Value("${app.sms.super-code:1234}")
    private String superCode;

    @Value("${app.invite-code.charset:ABCDEFGHJKLMNPQRSTUVWXYZ23456789}")
    private String charset;

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom RAND = new SecureRandom();

    /**
     * G1: 发送短信验证码（开发环境超级码1234，生产Redis存5分钟60s限流）
     */
    public void sendSms(String phone) {
        if (!PHONE.matcher(phone).matches()) throw new BusinessException(ErrorCode.PHONE_FORMAT_ERROR);
        String freqKey = "sms:freq:" + phone;
        Boolean ok = smsStore.setIfAbsent(freqKey, "1", 60, TimeUnit.SECONDS);
        if (ok != null && !ok) throw new BusinessException(ErrorCode.SMS_TOO_FREQUENT);

        String code;
        if (superCode != null && !superCode.isEmpty() && !"PROD".equals(System.getenv("ENV"))) {
            code = superCode;
            log.info("[SMS][DEV] phone={} 超级验证码={}", phone, code);
        } else {
            code = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            log.info("[SMS][PROD] phone={} 真实短信发送 code={}", phone, code);
        }
        smsStore.set("sms:code:" + phone, code, 5, TimeUnit.MINUTES);
    }

    /**
     * 内部：校验验证码（Dev超级码通过 或 Redis一致），正确后删除key
     */
    private void verifySmsCode(String phone, String inputCode) {
        if (!PHONE.matcher(phone).matches()) throw new BusinessException(ErrorCode.PHONE_FORMAT_ERROR);
        if (inputCode == null || inputCode.length() < 4) throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        if (superCode != null && superCode.equals(inputCode)) return;
        String saved = smsStore.get("sms:code:" + phone);
        if (!inputCode.equals(saved)) throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        smsStore.delete("sms:code:" + phone);
    }

    /**
     * 注册新用户 → 自动创建单身情侣组（未绑定partner_idx=1）
     */
    @Transactional
    public Map<String, Object> register(String phone, String smsCode, String nickname, String avatarUrl) {
        if (nickname == null || nickname.isBlank()) throw new BusinessException(ErrorCode.NICKNAME_EMPTY);
        if (nickname.length() > 20) throw new BusinessException(ErrorCode.NICKNAME_TOO_LONG);
        if (usersRepository.existsByPhone(phone)) throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        verifySmsCode(phone, smsCode);

        Couple couple = new Couple();
        couple.setInviteCodeP1(genInviteCode());
        couple.setInviteCodeP2(genInviteCode());
        couple.setCoinsTotal(0);
        couple = coupleRepository.save(couple);

        // 🔴T08 B1红线：建情侣立刻写 INIT_BALANCE流水 0币（将来改初始值自动对账diff=0）
        // 走 coinService.addCoins() 统一入口 → CoupleContext此时为空但fromUserId传null，因为还没登录用户上下文
        try {
            coinService.addCoins(couple.getId(), CoinReason.INIT_BALANCE, couple.getCoinsTotal(),
                    null, null, "init_balance:register:" + couple.getId());
        } catch (Exception e) {
            log.warn("[注册INIT流水失败 不影响注册，但B1对账会diff≠0] cid={} err={}", couple.getId(), e.getMessage());
        }

        Users u = new Users();
        u.setPhone(phone);
        u.setNickname(nickname);
        u.setAvatarUrl(avatarUrl == null ? "emoji:🌸#FFD5E5" : avatarUrl);
        u.setCoupleId(couple.getId());
        u.setPartnerIdx(1);
        u = usersRepository.save(u);

        CoupleContext ctx = CoupleContext.builder()
                .userId(u.getId()).phone(phone).nickname(u.getNickname())
                .coupleId(couple.getId()).partnerIdx(1).build();
        String token = jwtUtil.generateToken(ctx);
        return loginResp(token, u, couple);
    }

    /**
     * G3: 登录（手机号+验证码）→ 注册日送5金币 login_p1
     */
    @Transactional
    public Map<String, Object> login(String phone, String smsCode) {
        verifySmsCode(phone, smsCode);
        Users u = usersRepository.findByPhone(phone).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Couple couple = u.getCoupleId() != null
                ? coupleRepository.findById(u.getCoupleId()).orElse(null) : null;

        CoupleContext ctx = CoupleContext.builder()
                .userId(u.getId()).phone(phone).nickname(u.getNickname())
                .coupleId(u.getCoupleId()).partnerIdx(u.getPartnerIdx()).build();
        String token = jwtUtil.generateToken(ctx);

        if (couple != null) {
            try {
                String reason = u.getPartnerIdx() != null && u.getPartnerIdx() == 2
                        ? CoinReason.LOGIN_P2.getCode() : CoinReason.LOGIN_P1.getCode();
                String today = java.time.LocalDate.now().toString();
                String key = "dedupe:login:" + couple.getId() + ":" + u.getId() + ":" + today;
                Boolean first = smsStore.setIfAbsent(key, "1", 26, TimeUnit.HOURS);
                if (first != null && first) {
                    coinService.addCoins(couple.getId(),
                            u.getPartnerIdx() != null && u.getPartnerIdx() == 2 ? CoinReason.LOGIN_P2 : CoinReason.LOGIN_P1,
                            null, u.getId(), null, reason + ":" + today);
                }
            } catch (Exception e) {
                log.warn("[登录送币失败 不影响登录] uid={} err={}", u.getId(), e.getMessage());
            }
        }

        return loginResp(token, u, couple);
    }

    /**
     * G4: 退出登录 → JWT无状态 客户端删token即可，服务端记日志审计
     */
    public void logout() {
        Long uid = CoupleContext.currentUserId();
        log.info("[登出] userId={}", uid);
    }

    private Map<String, Object> loginResp(String token, Users u, Couple couple) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        Map<String, Object> user = new HashMap<>();
        user.put("id", u.getId());
        user.put("phone", u.getPhone());
        user.put("nickname", u.getNickname());
        user.put("avatarUrl", u.getAvatarUrl());
        user.put("partnerIdx", u.getPartnerIdx());
        user.put("coupleId", u.getCoupleId());
        resp.put("user", user);
        if (couple != null) {
            Map<String, Object> c = new HashMap<>();
            c.put("id", couple.getId());
            c.put("togetherDate", couple.getTogetherDate());
            c.put("coinsTotal", couple.getCoinsTotal());
            c.put("theme", couple.getTheme());
            c.put("inviteCodeP1", couple.getInviteCodeP1());
            c.put("inviteCodeP2", couple.getInviteCodeP2());
            resp.put("couple", c);
        }
        return resp;
    }

    public String genInviteCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(charset.charAt(RAND.nextInt(charset.length())));
        }
        return sb.toString();
    }
}
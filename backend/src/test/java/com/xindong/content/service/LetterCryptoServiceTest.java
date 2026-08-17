package com.xindong.content.service;

import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🔴红线3 LetterCryptoService AES 单测套件（6Case）")
class LetterCryptoServiceTest {

    @Autowired
    private LetterCryptoService cryptoService;

    private static final String PLAIN = "亲爱的，你知道吗？第一次见你我就心动了——2026-08-13；；SpecialChars: 🌟!@#$%^&*()你好世界";

    @Test
    @DisplayName("Case1: AES加密后不等于原文 + 是Hex字符串")
    void case1_encrypt_notEqualsPlain() {
        String cipher = cryptoService.encrypt(PLAIN);
        assertNotEquals(PLAIN, cipher, "加密后必须不等于原文");
        assertTrue(cipher.matches("^[0-9a-fA-F]+$"), "Encryptors.stronger()输出是HEX字符串");
        assertTrue(cipher.length() > 40, "AES-256-GCM密文长度应>=40");
    }

    @Test
    @DisplayName("Case2: 加密→解密 → 完全一致 往返验证(长中文/Emoji/SpecialChars)")
    void case2_roundTrip_perfectMatch() {
        String cipher = cryptoService.encrypt(PLAIN);
        String decrypted = cryptoService.decrypt(cipher);
        assertEquals(PLAIN, decrypted, "解密后必须与原文完全一致，UTF-8 + Emoji不丢失");
    }

    @Test
    @DisplayName("Case3: 密文篡改后解密 → 抛 50601 LETTER_AES_DECRYPT_FAILED (GCM完整性校验)")
    void case3_tamperedCiper_throw50601() {
        String cipher = cryptoService.encrypt("hello");
        StringBuilder sb = new StringBuilder(cipher);
        sb.setCharAt(5, (sb.charAt(5) == 'a') ? 'b' : 'a');
        String tampered = sb.toString();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cryptoService.decrypt(tampered), "🔴红线3: AES-GCM改动1位必须抛异常");
        assertEquals(ErrorCode.LETTER_AES_DECRYPT_FAILED, ex.getCode());
        assertEquals("50601", ex.getCode().getCode());
    }

    @Test
    @DisplayName("Case4: 🔴红线3 时光胶囊未到期 → 返回\"********\" 原文不泄露")
    void case4_redLine3_timeCapsule_notScheduled_maskStars() {
        String cipher = cryptoService.encrypt("生日惊喜信内容：去北海道看雪！");
        LocalDateTime future = LocalDateTime.now().plusDays(30);

        String out = cryptoService.decryptWithSchedule(cipher, future, true);
        assertEquals("********", out, "🔴红线3: 未到期时光胶囊固定返回8颗星 原文永不泄露");
        assertNotEquals("生日惊喜信内容：去北海道看雪！", out, "🔴红线3: 原文不能出现");
    }

    @Test
    @DisplayName("Case5: 🔴红线3 时光胶囊到期了（scheduledAt<now） → 正常明文解密")
    void case5_redLine3_timeCapsuleExpired_plainReturn() {
        String cipher = cryptoService.encrypt(PLAIN);
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);

        String out = cryptoService.decryptWithSchedule(cipher, past, true);
        assertEquals(PLAIN, out, "🔴红线3: 已到期/过期时光胶囊正常解密，返回原文");
    }

    @Test
    @DisplayName("Case6: 非时光胶囊(isTimeCapsule=false) → 无论scheduledAt是什么 都正常解密 不屏蔽")
    void case6_regularLetter_noMask() {
        String cipher = cryptoService.encrypt("普通情书内容");
        LocalDateTime farFuture = LocalDateTime.now().plusYears(1);

        String out = cryptoService.decryptWithSchedule(cipher, farFuture, false);
        assertEquals("普通情书内容", out, "非胶囊普通情书，虽然scheduledAt未到，但不屏蔽");
    }
}
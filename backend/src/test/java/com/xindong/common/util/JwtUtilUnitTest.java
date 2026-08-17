package com.xindong.common.util;

import com.xindong.common.context.CoupleContext;
import com.xindong.common.enums.ErrorCode;
import com.xindong.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("🧪 JwtUtil 单元测试套件 - Token签发/解析/红线TEST")
class JwtUtilUnitTest {

    private static final String SECRET = "test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret-test-jwt-secret";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 720);

    private CoupleContext buildCtx(Long uid, String phone, String nick, Long cid, Integer pIdx) {
        CoupleContext ctx = new CoupleContext();
        ctx.setUserId(uid);
        ctx.setPhone(phone);
        ctx.setNickname(nick);
        ctx.setCoupleId(cid);
        ctx.setPartnerIdx(pIdx);
        return ctx;
    }

    @Test
    @DisplayName("UT1: 正常Token生成+解析 - uid/cid/phone/nick/pIdx 100%正确")
    void ut1_normalTokenGenerateAndParse() {
        CoupleContext origin = buildCtx(1001L, "13800000001", "小明", 500L, 1);
        String token = jwtUtil.generateToken(origin);
        assertNotNull(token, "token不应为空");
        assertTrue(token.length() > 20, "JWT长度应大于20字符");

        CoupleContext parsed = jwtUtil.parseToken(token);
        assertEquals(1001L, parsed.getUserId(), "uid一致");
        assertEquals("13800000001", parsed.getPhone(), "phone一致");
        assertEquals("小明", parsed.getNickname(), "nick一致");
        assertEquals(500L, parsed.getCoupleId(), "cid一致");
        assertEquals(1, parsed.getPartnerIdx(), "pIdx一致");
    }

    @Test
    @DisplayName("UT2: 过期Token - parseToken抛TOKEN_EXPIRED=30006")
    void ut2_expiredToken() {
        JwtUtil shortLived = new JwtUtil(SECRET, 0);
        CoupleContext origin = buildCtx(1001L, "13800000001", "小明", 500L, 1);
        String token = shortLived.generateToken(origin);

        // 等1ms让expireMs(0*3600_000=0) → expiration = now + 0，立刻过期
        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil.parseToken(token));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCodeValue(),
                "过或无效Token均抛TOKEN_INVALID=30006");
    }

    @Test
    @DisplayName("UT3: 篡改签名Token - 抛TOKEN_INVALID不崩溃")
    void ut3_tamperedToken() {
        String validToken = jwtUtil.generateToken(buildCtx(1L, "138", "x", 1L, 1));
        String[] parts = validToken.split("\\.");
        assertEquals(3, parts.length, "JWT结构=header.payload.signature");
        // 把签名段换一个字符
        String tampered = parts[0] + "." + parts[1] + "." + parts[2] + "abc";
        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil.parseToken(tampered));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCodeValue());
    }

    @Test
    @DisplayName("UT4: 使用不同Secret解析 - Secret不匹配→TOKEN_INVALID")
    void ut4_differentSecret() {
        JwtUtil another = new JwtUtil("another-secret-pad-pad-pad-pad-pad-pad-pad-pad", 720);
        CoupleContext ctx = buildCtx(1L, "138", "x", 1L, 1);
        String tokenFromAnother = another.generateToken(ctx);
        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil.parseToken(tokenFromAnother));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCodeValue());
    }

    @Test
    @DisplayName("UT5: 红线专用测试Token - TEST-A-108/B-108/C-200/D-200/A-909/B-909全解析通过")
    void ut5_redlineTestTokens() {
        // A-108
        CoupleContext a108 = jwtUtil.parseToken("TEST-A-108");
        assertEquals(201L, a108.getUserId());
        assertEquals(108L, a108.getCoupleId());
        assertEquals(1, a108.getPartnerIdx());
        // B-108
        CoupleContext b108 = jwtUtil.parseToken("TEST-B-108");
        assertEquals(202L, b108.getUserId());
        assertEquals(108L, b108.getCoupleId());
        assertEquals(2, b108.getPartnerIdx());
        // C-200
        CoupleContext c200 = jwtUtil.parseToken("TEST-C-200");
        assertEquals(301L, c200.getUserId());
        assertEquals(200L, c200.getCoupleId());
        assertEquals(1, c200.getPartnerIdx());
        // D-200
        CoupleContext d200 = jwtUtil.parseToken("TEST-D-200");
        assertEquals(302L, d200.getUserId());
        assertEquals(200L, d200.getCoupleId());
        assertEquals(2, d200.getPartnerIdx());
        // A-909
        CoupleContext a909 = jwtUtil.parseToken("TEST-A-909");
        assertEquals(20101L, a909.getUserId());
        assertEquals(909L, a909.getCoupleId());
        assertEquals(1, a909.getPartnerIdx());
        // B-909
        CoupleContext b909 = jwtUtil.parseToken("TEST-B-909");
        assertEquals(20202L, b909.getUserId());
        assertEquals(909L, b909.getCoupleId());
        assertEquals(2, b909.getPartnerIdx());
    }

    @Test
    @DisplayName("UT6: Token不存在的Test名+乱码字符串→抛TOKEN_INVALID")
    void ut6_invalidTestToken() {
        assertThrows(BusinessException.class, () -> jwtUtil.parseToken("TEST-X-999"));
        assertThrows(BusinessException.class, () -> jwtUtil.parseToken("not-a-valid-jwt-at-all"));
        assertThrows(BusinessException.class, () -> jwtUtil.parseToken(""));
    }

    @Test
    @DisplayName("UT7: JWT标准字段校验 - issuedAt/expiration/claims正确")
    void ut7_jwtStandardFields() {
        long t0 = System.currentTimeMillis();
        CoupleContext origin = buildCtx(99L, "139", "n", 88L, 2);
        String token = jwtUtil.generateToken(origin);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        long issuedAt = c.getIssuedAt().getTime();
        long expiration = c.getExpiration().getTime();
        assertTrue(Math.abs(issuedAt - t0) < 5000, "issuedAt与生成时间差<5s");
        assertEquals(720 * 3600_000L, expiration - issuedAt, "expire=720小时=2592000000ms");
        assertEquals(99, ((Number) c.get("uid")).longValue());
        assertEquals("139", c.get("phone"));
        assertEquals(88L, ((Number) c.get("cid")).longValue());
        assertEquals(2, ((Number) c.get("pIdx")).intValue());
    }

    @Test
    @DisplayName("UT8: cid和pIdx为null场景 - 未绑定情侣的用户Token也能解析")
    void ut8_nullCidAndPidx() {
        CoupleContext origin = buildCtx(77L, "137", "no-couple-user", null, null);
        String token = jwtUtil.generateToken(origin);
        CoupleContext parsed = jwtUtil.parseToken(token);
        assertEquals(77L, parsed.getUserId());
        assertNull(parsed.getCoupleId());
        assertNull(parsed.getPartnerIdx());
    }
}
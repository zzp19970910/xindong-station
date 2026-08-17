package com.xindong.auth.controller;

import com.xindong.auth.service.AuthService;
import com.xindong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批次1-G 登录注册模块")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "G1 发送短信验证码 开发环境超级码1234")
    @PostMapping("/sms-code")
    public Result<Void> sendSms(@RequestBody Map<String, String> body) {
        authService.sendSms(body.get("phone"));
        return Result.success();
    }

    @Operation(summary = "G2 手机号注册(同时创建单身情侣组+送couple绑定邀请码)")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return Result.success(authService.register(
                body.get("phone"),
                body.get("smsCode"),
                body.get("nickname"),
                body.getOrDefault("avatarUrl", "emoji:🌸#FFD5E5")
        ));
    }

    @Operation(summary = "G3 手机号验证码登录(首次同日登录+5金币 login_p1/login_p2)")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.success(authService.login(body.get("phone"), body.get("smsCode")));
    }

    @Operation(summary = "G4 退出登录 JWT无状态 服务端仅审计日志")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
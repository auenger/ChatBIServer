package com.meper.chatbi.web.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 登录 / 登出 / 当前主体（阶段 1：引导管理员）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record MeResponse(String username, String role, String enforcementState) {
    }

    private final BootstrapAdminAuth adminAuth;
    private final TokenStore tokenStore;

    public AuthController(BootstrapAdminAuth adminAuth, TokenStore tokenStore) {
        this.adminAuth = adminAuth;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        if (!adminAuth.matches(request.username(), request.password())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        return Map.of(
                "token", tokenStore.issue(request.username()),
                "username", request.username(),
                "role", "BOOTSTRAP_ADMIN");
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestAttribute(AuthInterceptor.TOKEN_ATTR) String token) {
        tokenStore.revoke(token);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal) {
        return new MeResponse(principal, "BOOTSTRAP_ADMIN", "BOOTSTRAP_ADMIN_UNRESTRICTED");
    }
}

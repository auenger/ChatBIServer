package com.meper.chatbi.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Bearer token 认证拦截：校验通过后把已验证主体放入请求属性
 * {@link #PRINCIPAL_ATTR}，控制器经 @RequestAttribute 取用 —— ExecutionContext 只能由此生成。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTR = "meper.principal";
    public static final String TOKEN_ATTR = "meper.token";

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim() : null;
        var principal = tokenStore.authenticate(token);
        if (principal.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("code", "UNAUTHORIZED", "message", "未登录或会话已过期")));
            return false;
        }
        request.setAttribute(PRINCIPAL_ATTR, principal.get());
        request.setAttribute(TOKEN_ATTR, token);
        return true;
    }
}

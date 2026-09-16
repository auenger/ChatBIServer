package com.meper.chatbi.web.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话令牌（阶段 1）：不透明 token → 用户名，滑动过期 12 小时。
 * 重启即失效属预期；持久会话/多用户/刷新令牌随 P2 身份体系替换。
 */
@Component
public class TokenStore {

    private static final long TTL_SECONDS = 12 * 3600;

    private record Session(String username, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(username, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        sessions.replace(token, new Session(session.username(), Instant.now().plusSeconds(TTL_SECONDS)));
        return Optional.of(session.username());
    }

    public void revoke(String token) {
        sessions.remove(token);
    }
}

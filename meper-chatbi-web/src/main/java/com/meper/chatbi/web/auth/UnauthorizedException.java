package com.meper.chatbi.web.auth;

/** 认证失败（401）。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

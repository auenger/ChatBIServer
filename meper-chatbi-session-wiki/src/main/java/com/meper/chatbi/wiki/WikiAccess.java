package com.meper.chatbi.wiki;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 当前仅引导管理员可访问。P2 以实时资源策略替换此入口，不在 Controller 做资源判断。 */
@Component
public class WikiAccess {
    private final String admin;
    public WikiAccess(@Value("${meper.bootstrap-admin.username}") String admin) { this.admin=admin; }
    public void check(String principal,long datasourceId) {
        checkPrincipal(principal);
        if(datasourceId<=0) throw new IllegalArgumentException("数据源无效");
    }
    public void checkPrincipal(String principal) {if(principal==null||!admin.equals(principal))throw new Denied();}
    public static class Denied extends RuntimeException {
        public Denied() {super("当前 DataWiki 仅对引导管理员开放");}
    }
}

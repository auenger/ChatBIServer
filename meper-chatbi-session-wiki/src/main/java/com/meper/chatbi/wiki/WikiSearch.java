package com.meper.chatbi.wiki;
import java.util.regex.Pattern;

/** 受限路径模式：* 不跨目录，** 可跨目录；不执行 shell 或任意正则。 */
public final class WikiSearch {
    private WikiSearch() { }
    public static Pattern glob(String glob) {
        if(glob==null || glob.isBlank()) glob="/**";
        if(glob.length()>512 || !glob.startsWith("/") || glob.contains("..") || glob.contains("\\"))
            throw new IllegalArgumentException("路径模式须以 / 开头，不允许上级目录");
        StringBuilder regex=new StringBuilder("^");
        for(int i=0;i<glob.length();i++) {
            char c=glob.charAt(i);
            if(c=='*') {
                if(i+1<glob.length() && glob.charAt(i+1)=='*') {
                    i++; if(i+1<glob.length() && glob.charAt(i+1)=='/') {regex.append("(?:.*/)?");i++;} else regex.append(".*");
                } else regex.append("[^/]*");
            } else if(c=='?') regex.append("[^/]");
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.append('$').toString());
    }
}

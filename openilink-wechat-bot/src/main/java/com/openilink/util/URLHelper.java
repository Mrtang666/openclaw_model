package com.openilink.util;

public class URLHelper {
    private URLHelper() {}

    public static String joinPath(String base, String path) {
        if (base == null || base.isEmpty()) return path;
        if (path == null || path.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base);
        if (sb.charAt(sb.length() - 1) == '/') sb.setLength(sb.length() - 1);
        if (path.charAt(0) != '/') sb.append('/');
        sb.append(path);
        return sb.toString();
    }
}

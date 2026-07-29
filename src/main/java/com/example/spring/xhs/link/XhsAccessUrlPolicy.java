package com.example.spring.xhs.link;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class XhsAccessUrlPolicy {

    private static final Pattern POST_ID = Pattern.compile("[A-Za-z0-9_-]{1,191}");

    private XhsAccessUrlPolicy() {
    }

    public static String sanitize(String value, String sourcePostId) {
        if (value == null || value.isBlank() || sourcePostId == null
                || !POST_ID.matcher(sourcePostId.strip()).matches()) {
            return "";
        }
        try {
            URI uri = URI.create(value.strip());
            String host = uri.getHost();
            String expectedPath = "/explore/" + sourcePostId.strip();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !isXhsHost(host) || !expectedPath.equals(trimTrailingSlash(uri.getPath()))) {
                return "";
            }
            Map<String, String> parameters = allowedParameters(uri.getRawQuery());
            if (!parameters.containsKey("xsec_token")) {
                return "";
            }
            String query = parameters.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            return "https://" + host.toLowerCase(Locale.ROOT) + expectedPath + "?" + query;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static Map<String, String> allowedParameters(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            if (("xsec_token".equals(key) || "xsec_source".equals(key)) && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static boolean isXhsHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("xiaohongshu.com") || normalized.endsWith(".xiaohongshu.com");
    }

    private static String trimTrailingSlash(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

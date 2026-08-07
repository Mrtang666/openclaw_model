package com.example.spring.xhs.link;

import java.net.URI;
import java.util.Locale;

public final class XhsImageUrlPolicy {

    private XhsImageUrlPolicy() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value.strip());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return "";
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            if (!normalized.equals("xhscdn.com") && !normalized.endsWith(".xhscdn.com")
                    && !normalized.equals("xhscdn.net") && !normalized.endsWith(".xhscdn.net")) {
                return "";
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}

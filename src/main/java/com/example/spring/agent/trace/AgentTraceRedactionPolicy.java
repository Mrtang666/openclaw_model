package com.example.spring.agent.trace;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentTraceRedactionPolicy {

    private static final int DEFAULT_MAX_TEXT_LENGTH = 512;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b(\\d{3})\\d{3,}(\\d{4})\\b");
    private static final Pattern JSON_SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(\"(?i:password|token|secret|api_key|apikey|access_key|accesskey)\"\\s*:\\s*\")([^\"]*)(\")");
    private static final Pattern TEXT_SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "\\b(?i)(password|token|secret|api_key|apikey|access_key|accesskey)(\\s*[:=]\\s*)([^\\s,;}&]+)");

    private final int maxTextLength;

    public AgentTraceRedactionPolicy() {
        this(DEFAULT_MAX_TEXT_LENGTH);
    }

    AgentTraceRedactionPolicy(int maxTextLength) {
        this.maxTextLength = maxTextLength <= 0 ? DEFAULT_MAX_TEXT_LENGTH : maxTextLength;
    }

    public String redact(String value) {
        String text = clean(value);
        if (text.isBlank()) {
            return "";
        }
        String redacted = redactJsonSensitiveValues(text);
        redacted = redactTextSensitiveValues(redacted);
        redacted = redactEmails(redacted);
        redacted = redactLongNumbers(redacted);
        return truncate(redacted);
    }

    private String redactJsonSensitiveValues(String value) {
        return JSON_SENSITIVE_VALUE_PATTERN.matcher(value).replaceAll("$1[REDACTED]$3");
    }

    private String redactTextSensitiveValues(String value) {
        return TEXT_SENSITIVE_VALUE_PATTERN.matcher(value).replaceAll("$1$2[REDACTED]");
    }

    private String redactEmails(String value) {
        Matcher matcher = EMAIL_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "***" + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String redactLongNumbers(String value) {
        return LONG_NUMBER_PATTERN.matcher(value).replaceAll("$1****$2");
    }

    private String truncate(String value) {
        return value.length() <= maxTextLength ? value : value.substring(0, maxTextLength) + "... [TRUNCATED]";
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}

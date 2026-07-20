package com.example.spring.speech.voice;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrdinalChoiceParser {
    private static final Pattern ARABIC = Pattern.compile(
        "^(?:选择|选|使用|用|就用|就选)?(?:第)?(\\d{1,2})(?:个|项|号|种)?$");
    private static final Pattern CHINESE = Pattern.compile(
        "^(?:选择|选|使用|用|就用|就选)?(?:第)?([一二三四五六七八九十]{1,3})(?:个|项|号|种)?$");

    private OrdinalChoiceParser() {
    }

    public static OptionalInt parse(String input) {
        if (input == null || input.isBlank()) {
            return OptionalInt.empty();
        }
        String normalized = input.trim().replaceAll("[，。！？,.!?\\s]", "");
        Matcher arabic = ARABIC.matcher(normalized);
        if (arabic.matches()) {
            int value = Integer.parseInt(arabic.group(1));
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        }
        Matcher chinese = CHINESE.matcher(normalized);
        if (chinese.matches()) {
            int value = chineseNumber(chinese.group(1));
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        }
        return OptionalInt.empty();
    }

    private static int chineseNumber(String value) {
        if (value.length() == 1 && value.charAt(0) != '十') {
            return digit(value.charAt(0));
        }
        int ten = value.indexOf('十');
        if (ten < 0) {
            return -1;
        }
        int tens = ten == 0 ? 1 : digit(value.charAt(ten - 1));
        int units = ten == value.length() - 1 ? 0 : digit(value.charAt(ten + 1));
        return tens < 0 || units < 0 ? -1 : tens * 10 + units;
    }

    private static int digit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }
}

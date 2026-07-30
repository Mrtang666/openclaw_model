package com.example.spring.xhs.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class XhsReportScheduleCalculator {

    private XhsReportScheduleCalculator() {
    }

    static Instant next(String frequency, LocalTime runTime, Integer dayOfWeek, Integer dayOfMonth,
                        ZoneId zone, Instant after) {
        ZonedDateTime current = after.atZone(zone);
        LocalDate date = current.toLocalDate();
        return switch (frequency) {
            case "DAILY" -> future(date.atTime(runTime), current, zone, 1);
            case "WEEKLY" -> weekly(current, runTime, dayOfWeek == null ? 1 : dayOfWeek, zone);
            case "MONTHLY" -> monthly(current, runTime, dayOfMonth == null ? 1 : dayOfMonth, zone);
            default -> throw new IllegalArgumentException("frequency must be DAILY, WEEKLY or MONTHLY");
        };
    }

    static Instant periodStart(String frequency, Instant end, ZoneId zone) {
        ZonedDateTime value = end.atZone(zone);
        return switch (frequency) {
            case "WEEKLY" -> value.minusWeeks(1).toInstant();
            case "MONTHLY" -> value.minusMonths(1).toInstant();
            default -> value.minusDays(1).toInstant();
        };
    }

    private static Instant future(LocalDateTime candidate, ZonedDateTime current, ZoneId zone, int days) {
        ZonedDateTime value = candidate.atZone(zone);
        return value.isAfter(current) ? value.toInstant() : value.plusDays(days).toInstant();
    }

    private static Instant weekly(ZonedDateTime current, LocalTime time, int day, ZoneId zone) {
        int normalized = Math.max(1, Math.min(day, 7));
        LocalDate date = current.toLocalDate();
        int delta = Math.floorMod(normalized - date.getDayOfWeek().getValue(), 7);
        ZonedDateTime candidate = date.plusDays(delta).atTime(time).atZone(zone);
        return candidate.isAfter(current) ? candidate.toInstant() : candidate.plusWeeks(1).toInstant();
    }

    private static Instant monthly(ZonedDateTime current, LocalTime time, int day, ZoneId zone) {
        int normalized = Math.max(1, Math.min(day, 31));
        YearMonth month = YearMonth.from(current);
        LocalDate date = month.atDay(Math.min(normalized, month.lengthOfMonth()));
        ZonedDateTime candidate = date.atTime(time).atZone(zone);
        if (!candidate.isAfter(current)) {
            month = month.plusMonths(1);
            candidate = month.atDay(Math.min(normalized, month.lengthOfMonth())).atTime(time).atZone(zone);
        }
        return candidate.toInstant();
    }
}

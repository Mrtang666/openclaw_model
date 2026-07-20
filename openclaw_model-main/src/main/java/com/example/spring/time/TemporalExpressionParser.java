package com.example.spring.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TemporalExpressionParser {
    private static final Pattern FULL_DATE = Pattern.compile(
        "(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})日?");
    private static final Pattern MONTH_DAY = Pattern.compile(
        "(?<!\\d)(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern WEEKDAY = Pattern.compile(
        "(?:(本周|这周|下周)|周|星期)([一二三四五六日天])");
    private static final Map<String, DayOfWeek> DAYS = Map.of(
        "一", DayOfWeek.MONDAY,
        "二", DayOfWeek.TUESDAY,
        "三", DayOfWeek.WEDNESDAY,
        "四", DayOfWeek.THURSDAY,
        "五", DayOfWeek.FRIDAY,
        "六", DayOfWeek.SATURDAY,
        "日", DayOfWeek.SUNDAY,
        "天", DayOfWeek.SUNDAY);

    public DateIntent parse(String text, LocalDate currentDate) {
        String value = text == null ? "" : text;
        if (value.contains("大后天")) {
            return new DateIntent(currentDate.plusDays(3), "大后天", true, false);
        }
        if (value.contains("后天")) {
            return new DateIntent(currentDate.plusDays(2), "后天", true, false);
        }
        if (value.contains("明天") || value.contains("明日")) {
            return new DateIntent(currentDate.plusDays(1), "明天", true, false);
        }
        if (value.contains("今天") || value.contains("今日")) {
            return new DateIntent(currentDate, "今天", true, false);
        }
        if (containsCurrentMoment(value)) {
            return new DateIntent(currentDate, "现在", false, true);
        }

        Matcher fullDate = FULL_DATE.matcher(value);
        if (fullDate.find()) {
            LocalDate date = LocalDate.of(
                Integer.parseInt(fullDate.group(1)),
                Integer.parseInt(fullDate.group(2)),
                Integer.parseInt(fullDate.group(3)));
            return new DateIntent(date, fullDate.group(), true, false);
        }
        Matcher monthDay = MONTH_DAY.matcher(value);
        if (monthDay.find()) {
            MonthDay parsed = MonthDay.of(
                Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
            LocalDate date = parsed.atYear(currentDate.getYear());
            if (date.isBefore(currentDate)) {
                date = parsed.atYear(currentDate.plusYears(1).getYear());
            }
            return new DateIntent(date, monthDay.group(), true, false);
        }
        Matcher weekday = WEEKDAY.matcher(value);
        if (weekday.find()) {
            DayOfWeek target = DAYS.get(weekday.group(2));
            boolean nextWeek = "下周".equals(weekday.group(1));
            LocalDate date = nextWeek
                ? currentDate.plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(target))
                : currentDate.with(TemporalAdjusters.nextOrSame(target));
            return new DateIntent(date, weekday.group(), true, false);
        }
        return new DateIntent(currentDate, "现在", false, true);
    }

    public String removeDateExpressions(String text) {
        if (text == null) {
            return "";
        }
        return FULL_DATE.matcher(MONTH_DAY.matcher(WEEKDAY.matcher(text)
                .replaceAll(""))
                .replaceAll(""))
            .replaceAll("")
            .replace("大后天", "")
            .replace("后天", "")
            .replace("明天", "")
            .replace("明日", "")
            .replace("今天", "")
            .replace("今日", "");
    }

    private static boolean containsCurrentMoment(String text) {
        return text.contains("现在") || text.contains("当前")
            || text.contains("实时") || text.contains("此刻");
    }
}

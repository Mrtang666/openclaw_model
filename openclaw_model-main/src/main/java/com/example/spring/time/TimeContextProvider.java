package com.example.spring.time;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TimeContextProvider {
    private static final DateTimeFormatter DISPLAY =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE");

    private final Clock clock;
    private final ZoneId zoneId;

    @Autowired
    public TimeContextProvider(@Value("${app.time-zone:Asia/Shanghai}") String zoneId) {
        this(Clock.system(ZoneId.of(zoneId)), ZoneId.of(zoneId));
    }

    TimeContextProvider(Clock clock, ZoneId zoneId) {
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
    }

    public String modelContext() {
        ZonedDateTime now = now();
        return "本次消息处理时间：" + DISPLAY.format(now)
            + "，时区：" + zoneId
            + "。涉及今天、明天、后天、当前时间、星期或年份时，必须以该时间为基准；"
            + "容易混淆时请同时给出绝对日期。";
    }
}

package com.example.spring.time;

import java.time.LocalDate;

public record DateIntent(
    LocalDate targetDate,
    String expression,
    boolean explicit,
    boolean currentMoment) {
}

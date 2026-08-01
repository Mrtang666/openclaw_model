package com.example.spring.xhs.analysis;

public record XhsTrendSignals(
        double engagementGrowthPerHour,
        int recurrenceCount) {

    public XhsTrendSignals {
        engagementGrowthPerHour = Math.max(0, engagementGrowthPerHour);
        recurrenceCount = Math.max(0, recurrenceCount);
    }

    public static XhsTrendSignals empty() {
        return new XhsTrendSignals(0, 0);
    }
}

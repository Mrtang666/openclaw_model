package com.example.spring.xhs.incident;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XhsIncidentStatusTests {

    @Test
    void enforcesOperationalLifecycle() {
        assertThat(XhsIncidentStatus.OPEN.canTransitionTo(XhsIncidentStatus.ACKNOWLEDGED)).isTrue();
        assertThat(XhsIncidentStatus.OPEN.canTransitionTo(XhsIncidentStatus.INVESTIGATING)).isTrue();
        assertThat(XhsIncidentStatus.OPEN.canTransitionTo(XhsIncidentStatus.RESOLVED)).isFalse();
        assertThat(XhsIncidentStatus.ACKNOWLEDGED.canTransitionTo(XhsIncidentStatus.RESOLVED)).isFalse();
        assertThat(XhsIncidentStatus.INVESTIGATING.canTransitionTo(XhsIncidentStatus.RESOLVED)).isTrue();
        assertThat(XhsIncidentStatus.RESOLVED.canTransitionTo(XhsIncidentStatus.INVESTIGATING)).isTrue();
    }
}

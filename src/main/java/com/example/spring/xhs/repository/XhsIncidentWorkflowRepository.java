package com.example.spring.xhs.repository;

import com.example.spring.xhs.incident.XhsIncidentStatus;
import com.example.spring.xhs.incident.XhsIncidentTransition;

import java.time.Instant;

public interface XhsIncidentWorkflowRepository {

    XhsIncidentTransition transition(
            String projectKey,
            long incidentId,
            XhsIncidentStatus targetStatus,
            String connectionId,
            String recipientId,
            String note,
            Instant now);
}

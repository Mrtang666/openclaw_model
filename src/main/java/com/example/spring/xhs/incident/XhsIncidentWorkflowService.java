package com.example.spring.xhs.incident;

import com.example.spring.xhs.repository.XhsIncidentWorkflowRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class XhsIncidentWorkflowService {

    private static final int MAX_NOTE_LENGTH = 1000;

    private final XhsIncidentWorkflowRepository repository;

    public XhsIncidentWorkflowService(XhsIncidentWorkflowRepository repository) {
        this.repository = repository;
    }

    public XhsIncidentTransition transition(
            String projectKey,
            long incidentId,
            String targetStatus,
            String connectionId,
            String recipientId,
            String note) {
        if (incidentId <= 0) {
            throw new IllegalArgumentException("incident_id 必须是正整数");
        }
        String normalizedNote = note == null ? "" : note.strip();
        if (normalizedNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("处置说明不能超过 1000 个字符");
        }
        return repository.transition(
                required(projectKey, "project_key"), incidentId, XhsIncidentStatus.from(targetStatus),
                required(connectionId, "connection_id"), required(recipientId, "recipient_id"),
                normalizedNote, Instant.now());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }
}

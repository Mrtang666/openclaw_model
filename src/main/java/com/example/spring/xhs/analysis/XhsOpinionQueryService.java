package com.example.spring.xhs.analysis;

import com.example.spring.xhs.repository.XhsAnalysisRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class XhsOpinionQueryService {

    private final XhsAnalysisRepository repository;

    public XhsOpinionQueryService(XhsAnalysisRepository repository) {
        this.repository = repository;
    }

    public List<XhsOpinionView> search(String projectKey, String keyword, String sentiment, int minimumRiskScore, int limit) {
        return repository.searchOpinions(required(projectKey), safe(keyword), safe(sentiment), minimumRiskScore, limit <= 0 ? 10 : limit);
    }

    public List<XhsIncidentView> incidents(String projectKey, String status, int limit) {
        return repository.listIncidents(required(projectKey), safe(status), limit <= 0 ? 10 : limit);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project_key 不能为空");
        }
        return value.strip();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}

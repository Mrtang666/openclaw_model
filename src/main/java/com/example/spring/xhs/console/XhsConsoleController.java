package com.example.spring.xhs.console;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.link.XhsPostLinkService;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportDocxService;
import com.example.spring.xhs.schedule.XhsReportArtifactService;
import com.example.spring.xhs.schedule.XhsReportRunView;
import com.example.spring.xhs.schedule.XhsReportScheduleRequest;
import com.example.spring.xhs.schedule.XhsReportScheduleService;
import com.example.spring.xhs.schedule.XhsReportScheduleView;
import com.example.spring.xhs.schedule.XhsScheduledReportDeliveryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
@RequestMapping("/api/xhs-console")
public class XhsConsoleController {

    private final XhsConsoleService service;
    private final XhsConsoleHealthService healthService;
    private final XhsPostLinkService postLinkService;
    private final XhsDailyReportDocxService reportDocxService;
    private final XhsConsoleUrlService consoleUrlService;
    private final XhsReportScheduleService reportScheduleService;
    private final XhsScheduledReportDeliveryService reportDeliveryService;
    private final XhsReportArtifactService reportArtifactService;
    private final XhsAuthorizationService authorizationService;

    public XhsConsoleController(
            XhsConsoleService service,
            XhsConsoleHealthService healthService,
            XhsPostLinkService postLinkService,
            XhsDailyReportDocxService reportDocxService,
            XhsConsoleUrlService consoleUrlService,
            XhsReportScheduleService reportScheduleService,
            XhsScheduledReportDeliveryService reportDeliveryService,
            XhsReportArtifactService reportArtifactService,
            XhsAuthorizationService authorizationService) {
        this.service = service;
        this.healthService = healthService;
        this.postLinkService = postLinkService;
        this.reportDocxService = reportDocxService;
        this.consoleUrlService = consoleUrlService;
        this.reportScheduleService = reportScheduleService;
        this.reportDeliveryService = reportDeliveryService;
        this.reportArtifactService = reportArtifactService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public XhsConsoleHealth health() {
        return healthService.health();
    }

    @GetMapping("/authorization")
    public XhsAuthorizationService.AuthorizationStatus authorization() {
        return authorizationService.status();
    }

    @PostMapping("/authorization/validate")
    public XhsAuthorizationService.AuthorizationStatus validateAuthorization() {
        return authorizationService.validate();
    }

    @PostMapping("/authorization/cookie")
    public XhsAuthorizationService.AuthorizationStatus updateAuthorizationCookie(
            @RequestBody AuthorizationCookieRequest request) {
        return authorizationService.updateCookie(request.cookie());
    }

    @PostMapping("/authorization/qr")
    @ResponseStatus(HttpStatus.CREATED)
    public XhsAuthorizationService.QrAuthorization startAuthorizationQr() {
        return authorizationService.startQr();
    }

    @GetMapping("/authorization/qr/{sessionId}")
    public XhsAuthorizationService.QrAuthorization pollAuthorizationQr(@PathVariable String sessionId) {
        return authorizationService.pollQr(sessionId);
    }

    @DeleteMapping("/authorization")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAuthorization() {
        authorizationService.clear();
    }

    @GetMapping("/overview")
    public XhsConsoleService.OverviewView overview(
            @RequestParam(required = false) String projectKey) {
        return service.overview(projectKey);
    }

    @GetMapping("/projects")
    public List<XhsConsoleService.ProjectView> projects() {
        return service.projects();
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public XhsConsoleService.ProjectView createProject(@RequestBody ProjectRequest request) {
        return service.createProject(request.projectKey(), request.name(), request.terms());
    }

    @PatchMapping("/projects/{projectKey}")
    public XhsConsoleService.ProjectView updateProject(
            @PathVariable String projectKey,
            @RequestBody ProjectRequest request) {
        return service.updateProject(projectKey, request.name(), request.status(), request.terms());
    }

    @DeleteMapping("/projects/{projectKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable String projectKey, @RequestBody DeleteProjectRequest request) {
        service.deleteProject(projectKey, request.confirmation());
    }

    @PostMapping("/projects/{projectKey}/collections")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> collect(
            @PathVariable String projectKey,
            @RequestBody(required = false) CollectionRequest request) {
        CollectionRequest body = request == null ? new CollectionRequest("", 20) : request;
        return Map.of("jobKey", service.startCollection(projectKey, body.query(), body.limit()));
    }

    @GetMapping("/jobs")
    public List<XhsConsoleService.JobView> jobs(
            @RequestParam(required = false) String projectKey,
            @RequestParam(defaultValue = "50") int limit) {
        return service.jobs(projectKey, limit);
    }

    @GetMapping("/opinions")
    public List<XhsConsoleService.OpinionRow> opinions(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) Instant publishedFrom,
            @RequestParam(required = false) Instant publishedTo,
            @RequestParam(defaultValue = "publishedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") int minimumRiskScore,
            @RequestParam(defaultValue = "50") int limit) {
        return service.opinions(projectKey, keyword, sentiment, publishedFrom, publishedTo,
                sortBy, sortDirection, minimumRiskScore, limit);
    }

    @GetMapping("/posts/{postId}")
    public XhsConsoleService.PostDetail post(@PathVariable long postId) {
        return service.post(postId);
    }

    @GetMapping("/posts/{postId}/open")
    public ResponseEntity<?> openPost(@PathVariable long postId) {
        try {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(postLinkService.accessUri(postId))
                    .cacheControl(CacheControl.noStore())
                    .build();
        } catch (IllegalArgumentException exception) {
            return linkError(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            return linkError(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping("/incidents")
    public List<XhsIncidentView> incidents(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return service.incidents(projectKey, status, limit);
    }

    @PostMapping("/incidents/{incidentId}/transitions")
    public XhsConsoleService.IncidentTransitionView transition(
            @PathVariable long incidentId,
            @RequestBody TransitionRequest request) {
        return service.transitionIncident(incidentId, request.targetStatus(), request.note());
    }

    @GetMapping("/reports/daily")
    public XhsDailyReport dailyReport(@RequestParam String projectKey, @RequestParam(required = false) String date) {
        return service.dailyReport(projectKey, date);
    }

    @GetMapping("/reports/daily.docx")
    public ResponseEntity<byte[]> downloadDailyReport(
            @RequestParam String projectKey,
            @RequestParam(required = false) String date) {
        XhsDailyReportDocxService.ReportDocument document = reportDocxService.generate(
                service.dailyReport(projectKey, date), consoleUrlService);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(document.bytes(), headers, HttpStatus.OK);
    }

    @GetMapping("/report-schedules")
    public List<XhsReportScheduleView> reportSchedules(
            @RequestParam(required = false) String projectKey) {
        return reportScheduleService.schedules(projectKey);
    }

    @PostMapping("/report-schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public XhsReportScheduleView createReportSchedule(@RequestBody XhsReportScheduleRequest request) {
        return reportScheduleService.create(request);
    }

    @PatchMapping("/report-schedules/{scheduleId}")
    public XhsReportScheduleView updateReportSchedule(
            @PathVariable long scheduleId, @RequestBody XhsReportScheduleRequest request) {
        return reportScheduleService.update(scheduleId, request);
    }

    @DeleteMapping("/report-schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReportSchedule(@PathVariable long scheduleId) {
        reportScheduleService.delete(scheduleId);
    }

    @PostMapping("/report-schedules/{scheduleId}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Long> runReportSchedule(@PathVariable long scheduleId) {
        return Map.of("runId", reportScheduleService.queueNow(scheduleId));
    }

    @GetMapping("/report-runs")
    public List<XhsReportRunView> reportRuns(
            @RequestParam(required = false) String projectKey,
            @RequestParam(defaultValue = "50") int limit) {
        return reportScheduleService.runs(projectKey, limit);
    }

    @GetMapping("/report-runs/{runId}")
    public XhsReportRunView reportRun(@PathVariable long runId) {
        return reportScheduleService.run(runId);
    }

    @PostMapping("/report-deliveries/{deliveryId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retryReportDelivery(@PathVariable long deliveryId) {
        reportDeliveryService.retry(deliveryId);
    }

    @GetMapping("/report-artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> downloadReportArtifact(@PathVariable long artifactId) {
        XhsReportArtifactService.Download document = reportArtifactService.download(artifactId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(document.bytes(), headers, HttpStatus.OK);
    }

    @GetMapping("/alert-rules")
    public List<XhsConsoleService.AlertRuleView> alertRules(
            @RequestParam(required = false) String projectKey) {
        return service.alertRules(projectKey);
    }

    @PostMapping("/alert-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public XhsConsoleService.AlertRuleView createAlertRule(@RequestBody AlertRuleRequest request) {
        return service.saveAlertRule(null, request.projectKey(), request.name(), request.minimumRiskScore(),
                request.cooldownMinutes(), request.enabled());
    }

    @PatchMapping("/alert-rules/{ruleId}")
    public XhsConsoleService.AlertRuleView updateAlertRule(
            @PathVariable long ruleId, @RequestBody AlertRuleRequest request) {
        return service.saveAlertRule(ruleId, request.projectKey(), request.name(), request.minimumRiskScore(),
                request.cooldownMinutes(), request.enabled());
    }

    @GetMapping("/alert-events")
    public List<XhsConsoleService.AlertEventView> alertEvents(
            @RequestParam(required = false) String projectKey,
            @RequestParam(defaultValue = "50") int limit) {
        return service.alertEvents(projectKey, limit);
    }

    @PostMapping("/alert-events/{eventId}/acknowledge")
    public XhsConsoleService.AlertEventView acknowledgeAlertEvent(@PathVariable long eventId) {
        return service.acknowledgeAlertEvent(eventId);
    }

    public record ProjectRequest(String projectKey, String name, String status, List<String> terms) {
    }

    public record CollectionRequest(String query, int limit) {
    }

    public record DeleteProjectRequest(String confirmation) {
    }

    public record TransitionRequest(String targetStatus, String note) {
    }

    public record AlertRuleRequest(String projectKey, String name, int minimumRiskScore,
                                   int cooldownMinutes, boolean enabled) {
    }

    public record AuthorizationCookieRequest(String cookie) {
    }

    private ResponseEntity<String> linkError(HttpStatus status, String message) {
        String safeMessage = escapeHtml(message == null || message.isBlank() ? "原帖链接暂不可用" : message);
        String html = """
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>原帖链接暂不可用</title><style>
                body{margin:0;background:#f5f6f8;color:#20242a;font-family:"Microsoft YaHei",sans-serif}
                main{max-width:560px;margin:12vh auto;padding:28px;background:#fff;border:1px solid #e3e6ea;border-radius:8px}
                h1{font-size:20px;margin:0 0 14px}p{line-height:1.7;color:#6d747d}
                a{display:inline-block;margin-top:12px;padding:9px 14px;background:#e9344a;color:#fff;text-decoration:none;border-radius:6px}
                </style></head><body><main><h1>原帖链接暂不可用</h1><p>%s</p>
                <a href="/xhs-console/index.html">返回舆情管理台</a></main></body></html>
                """.formatted(safeMessage);
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .body(html);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

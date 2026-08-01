package com.example.spring.wechat.report;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

@RestController
public class WechatReportController {

    private final WechatReportService reportService;

    public WechatReportController(WechatReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping(value = "/r/{reportId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report(@PathVariable String reportId) throws IOException {
        return reportService.find(reportId)
                .map(report -> {
                    try {
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                                .contentType(MediaType.TEXT_HTML)
                                .body(Files.readString(report.path()));
                    } catch (IOException exception) {
                        return ResponseEntity.internalServerError()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body("报告读取失败");
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

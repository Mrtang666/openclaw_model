package com.example.spring.wechat.login;

import com.example.spring.wechat.bot.WechatBotService;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotManagerSnapshot;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/clawbot")
public class ClawBotManagementController {

    private final WechatBotService botService;

    public ClawBotManagementController(WechatBotService botService) {
        this.botService = botService;
    }

    @GetMapping("/connections")
    public ResponseEntity<ClawBotManagerSnapshot> connections() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(botService.managerSnapshot());
    }

    @PostMapping("/connections")
    public ResponseEntity<?> addConnection() {
        try {
            ClawBotConnectionSnapshot connection = botService.addConnection();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(connection);
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/connections/{connectionId}")
    public ResponseEntity<Void> stopConnection(@PathVariable String connectionId) {
        return botService.stopConnection(connectionId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/connections/{connectionId}/reconnect")
    public ResponseEntity<?> reconnect(@PathVariable String connectionId) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(botService.reconnectConnection(connectionId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", exception.getMessage()));
        }
    }
}

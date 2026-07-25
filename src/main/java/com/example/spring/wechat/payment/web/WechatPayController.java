package com.example.spring.wechat.payment.web;

import com.example.spring.wechat.payment.service.WechatPayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wechat-pay")
public class WechatPayController {
    private final WechatPayService service;
    public WechatPayController(WechatPayService service) { this.service = service; }

    @PostMapping("/notify")
    public ResponseEntity<String> notify(@RequestBody(required = false) String body, @RequestHeader("Wechatpay-Serial") String serial, @RequestHeader("Wechatpay-Timestamp") String timestamp, @RequestHeader("Wechatpay-Nonce") String nonce, @RequestHeader("Wechatpay-Signature") String signature) {
        try { service.handleNotify(body,serial,timestamp,nonce,signature); return ResponseEntity.ok("成功"); }
        catch (UnsupportedOperationException e) { return ResponseEntity.status(501).body(e.getMessage()); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/refund/{paymentId}")
    public ResponseEntity<String> refund(@PathVariable String paymentId, @RequestParam(defaultValue="用户申请退款") String reason) {
        try { service.refund(paymentId, reason); return ResponseEntity.ok("退款已提交"); }
        catch (UnsupportedOperationException e) { return ResponseEntity.status(501).body(e.getMessage()); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}

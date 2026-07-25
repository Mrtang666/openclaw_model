package com.example.spring.wechat.payment.service;

import com.example.spring.wechat.payment.config.WechatPayProperties;
import com.example.spring.wechat.payment.model.WechatPaymentOrder;
import com.example.spring.wechat.taxi.model.RideOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Service
public class WechatPayService {
    private final WechatPayProperties properties; private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public WechatPayService(WechatPayProperties p, JdbcTemplate jdbc, ObjectMapper mapper){this.properties=p;this.jdbc=jdbc;this.mapper=mapper;}

    public WechatPaymentOrder create(RideOrder rideOrder, String openId) {
        if(!properties.enabled()) throw new IllegalStateException("微信支付尚未配置，当前请在滴滴端完成支付");
        if(rideOrder.finalFare()==null) throw new IllegalStateException("订单尚未产生最终车费，暂不能发起支付");
        if(openId==null||openId.isBlank()) throw new IllegalArgumentException("微信支付需要用户 openid");
        String id=UUID.randomUUID().toString();
        try {
            X509Certificate cert=loadCertificate(properties.platformCertificatePath()); PrivateKey key=loadPrivateKey(properties.privateKeyPath());
            var client=WechatPayHttpClientBuilder.create().withMerchant(properties.appId(),properties.mchId(),key).withWechatPay(properties.merchantSerialNumber(),cert.getPublicKey()).build();
            long cents=rideOrder.finalFare().movePointRight(2).longValueExact();
            var json=mapper.createObjectNode().put("appid",properties.appId()).put("mchid",properties.mchId()).put("description","滴滴打车订单").put("out_trade_no",id).put("notify_url",properties.notifyUrl());
            json.putObject("amount").put("total",cents).put("currency","CNY"); json.putObject("payer").put("openid",openId);
            HttpPost post=new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi"); post.setHeader("Accept","application/json"); post.setHeader("Content-Type","application/json"); post.setEntity(new StringEntity(json.toString(),ContentType.APPLICATION_JSON));
            try(var response=client.execute(post)){String body=EntityUtils.toString(response.getEntity());if(response.getStatusLine().getStatusCode()/100!=2)throw new IllegalStateException("微信下单失败: "+body);String prepay=mapper.readTree(body).path("prepay_id").asText();jdbc.update("INSERT INTO payment_orders(payment_id,ride_order_id,amount,channel,status,created_at,raw_json) VALUES(?,?,?,?,?,NOW(3),?)",id,rideOrder.orderId(),rideOrder.finalFare(),"WECHAT_JSAPI","CREATED",body);return new WechatPaymentOrder(id,rideOrder.orderId(),rideOrder.finalFare(),"CREATED",prepay,null);}
        } catch(Exception e){ throw new IllegalStateException("微信支付下单失败",e); }
    }

    public void handleNotify(String body,String serial,String timestamp,String nonce,String signature){
        if(!properties.enabled()) throw new IllegalStateException("微信支付未启用");
        try {
            X509Certificate cert=loadCertificate(properties.platformCertificatePath());
            NotificationHandler handler=new NotificationHandler(new CertificatesVerifier(java.util.List.of(cert)), properties.apiV3Key().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Notification n=handler.parse(new NotificationRequest.Builder().withSerialNumber(serial).withTimestamp(timestamp).withNonce(nonce).withSignature(signature).withBody(body).build());
            JsonNode data=mapper.readTree(n.getDecryptData());
            String paymentId=data.path("out_trade_no").asText(); String transaction=data.path("transaction_id").asText(); String state=data.path("trade_state").asText();
            jdbc.update("UPDATE payment_orders SET status=?,transaction_id=?,paid_at=IF(?='SUCCESS',NOW(3),paid_at),raw_json=? WHERE payment_id=?",state,transaction,state,data.toString(),paymentId);
            if("SUCCESS".equals(state)) jdbc.update("UPDATE ride_orders SET status='PAID',updated_at=NOW(3) WHERE order_id=(SELECT ride_order_id FROM payment_orders WHERE payment_id=?)",paymentId);
        } catch(Exception e){ throw new IllegalArgumentException("微信支付回调验签或解密失败",e); }
    }

    public void refund(String paymentId,String reason){
        if(!properties.enabled()) throw new IllegalStateException("微信支付未启用");
        if(paymentId==null||paymentId.isBlank()) throw new IllegalArgumentException("缺少支付订单号");
        requireCredentials();
        try {
            X509Certificate cert=loadCertificate(properties.platformCertificatePath());
            PrivateKey key=loadPrivateKey(properties.privateKeyPath());
            var client=WechatPayHttpClientBuilder.create().withMerchant(properties.appId(),properties.mchId(),key).withWechatPay(properties.merchantSerialNumber(),cert.getPublicKey()).build();
            HttpPost post=new HttpPost("https://api.mch.weixin.qq.com/v3/refund/domestic/refunds");
            post.setHeader("Accept","application/json"); post.setHeader("Content-Type","application/json");
            java.math.BigDecimal amount=jdbc.queryForObject("SELECT amount FROM payment_orders WHERE payment_id=?",java.math.BigDecimal.class,paymentId);
            if(amount==null) throw new IllegalArgumentException("支付订单不存在"); long cents=amount.movePointRight(2).longValueExact();
            post.setEntity(new StringEntity(mapper.createObjectNode().put("out_trade_no",paymentId).put("out_refund_no",paymentId+"-refund").put("reason",reason).putObject("amount").put("refund",cents).put("total",cents).put("currency","CNY").toString(), ContentType.APPLICATION_JSON));
            try(var response=client.execute(post)){ if(response.getStatusLine().getStatusCode()/100!=2) throw new IllegalStateException("微信退款请求失败: "+response.getStatusLine()); jdbc.update("UPDATE payment_orders SET status='REFUND_REQUESTED' WHERE payment_id=?",paymentId); EntityUtils.consumeQuietly(response.getEntity()); }
        } catch(Exception e){ throw new IllegalStateException("微信退款失败",e); }
    }

    private void requireCredentials(){if(properties.privateKeyPath().isBlank()||properties.platformCertificatePath().isBlank()||properties.merchantSerialNumber().isBlank())throw new IllegalStateException("缺少微信支付商户私钥、平台证书或序列号");}
    private X509Certificate loadCertificate(String path)throws Exception{if(path==null||path.isBlank())throw new IllegalStateException("未配置微信支付平台证书");try(var in=Files.newInputStream(Path.of(path))){return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}}
    @SuppressWarnings("unused") private PrivateKey loadPrivateKey(String path)throws Exception{String pem=Files.readString(Path.of(path)).replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s","");return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));}
}

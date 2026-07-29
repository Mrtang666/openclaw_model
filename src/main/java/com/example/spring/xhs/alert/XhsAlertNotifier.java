package com.example.spring.xhs.alert;

public interface XhsAlertNotifier {

    void send(XhsAlertDelivery delivery, String message);
}

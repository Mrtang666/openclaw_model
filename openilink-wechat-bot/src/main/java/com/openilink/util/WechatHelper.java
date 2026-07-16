package com.openilink.util;

import java.util.concurrent.ThreadLocalRandom;

public class WechatHelper {
    private WechatHelper() {}

    public static String randomWechatUIN() {
        long uin = 10000000L + ThreadLocalRandom.current().nextLong(90000000L);
        return String.valueOf(uin);
    }
}

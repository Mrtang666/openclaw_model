package com.openilink.util;

public class SleepHelper {
    private SleepHelper() {}

    public static boolean sleepInterruptibly(long millis) {
        try { Thread.sleep(millis); return false; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return true; }
    }
}

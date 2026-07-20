package com.example.spring.speech;

public record PendingVoiceReply(
    long id,
    String batchId,
    String userId,
    long messageId,
    int sequence,
    VoiceDeliveryAsset asset,
    boolean voiceSent) {
}

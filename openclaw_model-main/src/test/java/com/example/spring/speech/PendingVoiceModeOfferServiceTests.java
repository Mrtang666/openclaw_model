package com.example.spring.speech;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PendingVoiceModeOfferServiceTests {
    @Test
    void acceptsOrDeclinesOnlyAfterAnOffer() {
        PendingVoiceModeOfferService service = new PendingVoiceModeOfferService();
        assertThat(service.consume("user", "需要"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.NONE);

        service.offer("user");
        assertThat(service.consume("user", "需要开启"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.ACCEPT);

        service.offer("user");
        assertThat(service.consume("user", "不用了"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.DECLINE);

        service.offer("user");
        assertThat(service.consume("user", "可以打开语音"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.ACCEPT);
    }

    @Test
    void substantiveNewQuestionClearsTheOffer() {
        PendingVoiceModeOfferService service = new PendingVoiceModeOfferService();
        service.offer("user");

        assertThat(service.consume("user", "帮我查询无锡天气"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.NEW_REQUEST);
        assertThat(service.hasPendingOffer("user")).isFalse();
    }

    @Test
    void canCancelAnOfferWhenVoiceDeliveryFails() {
        PendingVoiceModeOfferService service = new PendingVoiceModeOfferService();
        service.offer("user");

        service.cancel("user");

        assertThat(service.hasPendingOffer("user")).isFalse();
        assertThat(service.consume("user", "需要"))
            .isEqualTo(PendingVoiceModeOfferService.OfferDecision.NONE);
    }
}

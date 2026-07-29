package com.example.spring.wechat.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WechatConversationModeTests {

    @Test
    void mapsCareRolesToTheThreeSupportedConversationModes() {
        assertThat(WechatConversationMode.fromRequestedRole("PATIENT"))
                .isEqualTo(WechatConversationMode.PATIENT);
        assertThat(WechatConversationMode.fromRequestedRole("CAREGIVER"))
                .isEqualTo(WechatConversationMode.CAREGIVER);
        assertThat(WechatConversationMode.fromRequestedRole("FAMILY"))
                .isEqualTo(WechatConversationMode.CAREGIVER);
        assertThat(WechatConversationMode.fromRequestedRole("DOCTOR"))
                .isEqualTo(WechatConversationMode.DOCTOR);
        assertThat(WechatConversationMode.fromRequestedRole("NURSE"))
                .isEqualTo(WechatConversationMode.DOCTOR);
        assertThat(WechatConversationMode.fromRequestedRole(null))
                .isEqualTo(WechatConversationMode.GENERAL);
        assertThat(WechatConversationMode.fromRequestedRole("unknown"))
                .isEqualTo(WechatConversationMode.GENERAL);
    }
}

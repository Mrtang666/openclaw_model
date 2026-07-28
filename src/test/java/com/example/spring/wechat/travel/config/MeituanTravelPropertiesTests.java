package com.example.spring.wechat.travel.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeituanTravelPropertiesTests {

    @Test
    void appliesSafeDefaultsAndTrimsValues() {
        MeituanTravelProperties properties = new MeituanTravelProperties(
                true, " token ", " ", " script.js ", " ", 0, 0);

        assertThat(properties.token()).isEqualTo("token");
        assertThat(properties.executable()).isEqualTo("ht-ai");
        assertThat(properties.cliScript()).isEqualTo("script.js");
        assertThat(properties.channel()).isEqualTo("meituan-developer");
        assertThat(properties.timeoutMs()).isEqualTo(125_000);
        assertThat(properties.maxOutputBytes()).isEqualTo(2_097_152);
    }
}

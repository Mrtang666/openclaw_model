package com.example.spring.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeatherAgentTests {
    @Test
    void extractsCityDistrictAndCountyNamesFromNaturalLanguage() {
        assertThat(WeatherAgent.extractRegion("请帮我查询江苏无锡滨湖区现在的天气"))
            .isEqualTo("江苏无锡滨湖区");
        assertThat(WeatherAgent.extractRegion("今天北京多少度？"))
            .isEqualTo("北京");
        assertThat(WeatherAgent.extractRegion("查一下河北省雄县实时气温"))
            .isEqualTo("河北省雄县");
    }
}

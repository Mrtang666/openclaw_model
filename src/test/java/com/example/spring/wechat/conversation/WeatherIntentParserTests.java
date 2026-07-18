package com.example.spring.wechat.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherIntentParserTests {

    private final WeatherIntentParser parser = new WeatherIntentParser();

    @Test
    void extractsCityBeforeWeatherKeyword() {
        assertThat(parser.extractCity("北京今天天气怎么样")).contains("北京");
        assertThat(parser.extractCity("帮我查一下上海天气")).contains("上海");
        assertThat(parser.extractCity("今天南京天气如何")).contains("南京");
        assertThat(parser.extractCity("帮我查看今天杭州的天气怎么样")).contains("杭州");
        assertThat(parser.extractCity("帮我看看北京明天适合出门吗")).contains("北京");
        assertThat(parser.extractCity("广州今天穿什么衣服合适")).contains("广州");
    }

    @Test
    void extractsCityAfterWeatherKeyword() {
        assertThat(parser.extractCity("天气 北京")).contains("北京");
        assertThat(parser.extractCity("查天气：广州")).contains("广州");
    }

    @Test
    void ignoresWeatherQuestionWithoutCity() {
        assertThat(parser.extractCity("今天天气怎么样")).isEmpty();
        assertThat(parser.extractCity("天气真好")).isEmpty();
    }
}

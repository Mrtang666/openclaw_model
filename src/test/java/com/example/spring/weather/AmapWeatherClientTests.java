package com.example.spring.weather;

import com.example.spring.exception.WeatherServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapWeatherClientTests {

    @Test
    void resolvesCityNameAndMapsLiveWeatherWithForecast() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapWeatherClient client = new AmapWeatherClient(
                builder, "test-key", "https://restapi.amap.com");

        server.expect(once(), requestTo(allOf(
                        containsString("/v3/config/district"),
                        containsString("keywords=%E8%A5%BF%E5%AE%89"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "districts": [
                            {"name": "西安区", "level": "district", "adcode": "220403"},
                            {"name": "西安市", "level": "city", "adcode": "610100"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(
                        containsString("/v3/weather/weatherInfo"),
                        containsString("city=610100"),
                        containsString("extensions=base"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "lives": [{
                            "province": "陕西",
                            "city": "西安市",
                            "weather": "晴",
                            "temperature": "32",
                            "winddirection": "西南",
                            "windpower": "4",
                            "humidity": "40",
                            "reporttime": "2026-07-16 11:30:00"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(
                        containsString("/v3/weather/weatherInfo"),
                        containsString("city=610100"),
                        containsString("extensions=all"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "forecasts": [{
                            "casts": [{
                              "date": "2026-07-16",
                              "week": "4",
                              "dayweather": "晴",
                              "nightweather": "多云",
                              "daytemp": "35",
                              "nighttemp": "25",
                              "daywind": "西南",
                              "nightwind": "西南",
                              "daypower": "1-3",
                              "nightpower": "1-3"
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherResult result = client.query("西安");

        assertThat(result.province()).isEqualTo("陕西");
        assertThat(result.city()).isEqualTo("西安市");
        assertThat(result.weather()).isEqualTo("晴");
        assertThat(result.temperature()).isEqualTo("32");
        assertThat(result.forecasts()).containsExactly(new WeatherResult.Forecast(
                "2026-07-16", "4", "晴", "多云", "35", "25",
                "西南", "西南", "1-3", "1-3"));
        server.verify();
    }

    @Test
    void rejectsProvinceInputBecauseWeatherCommandRequiresCity() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapWeatherClient client = new AmapWeatherClient(
                builder, "test-key", "https://restapi.amap.com");

        server.expect(once(), requestTo(containsString("/v3/config/district")))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "districts": [
                            {"name": "山西省", "level": "province", "adcode": "140000"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query("山西"))
                .isInstanceOf(WeatherServiceException.class)
                .hasMessage("请输入城市名，不要输入省份：山西");
        server.verify();
    }

    @Test
    void rejectsDistrictInputBecauseWeatherCommandRequiresCity() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapWeatherClient client = new AmapWeatherClient(
                builder, "test-key", "https://restapi.amap.com");

        server.expect(once(), requestTo(containsString("/v3/config/district")))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "districts": [
                            {"name": "朝阳区", "level": "district", "adcode": "110105"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query("朝阳区"))
                .isInstanceOf(WeatherServiceException.class)
                .hasMessage("请输入城市名，不要输入区县：朝阳区");
        server.verify();
    }

    @Test
    void rejectsMissingKey() {
        AmapWeatherClient client = new AmapWeatherClient(
                RestClient.builder(), "", "https://restapi.amap.com");

        assertThatThrownBy(() -> client.query("南京"))
                .isInstanceOf(WeatherServiceException.class)
                .hasMessage("未配置高德天气 KEY");
    }
}

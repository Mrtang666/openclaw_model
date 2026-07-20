package com.example.spring.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spring.weather.WeatherModels.WeatherReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WeatherServiceTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queriesRealtimeWeatherForDistrict() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/geo/v2/city/lookup", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-QW-Api-Key"));
            respond(exchange, """
                {"code":"200","location":[{"id":"101190202","name":"滨湖区",
                "adm2":"无锡市","adm1":"江苏省","country":"中国"}]}
                """);
        });
        server.createContext("/v7/weather/now", exchange -> respondGzip(exchange, """
            {"code":"200","updateTime":"2026-07-17T15:00+08:00","now":{
            "temp":"31","feelsLike":"35","text":"多云","humidity":"68",
            "windDir":"东南风","windScale":"3","windSpeed":"14"}}
            """));
        server.start();

        WeatherService service = service();
        WeatherReport report = service.currentWeather("江苏无锡滨湖区");

        assertThat(apiKey.get()).isEqualTo("weather-test-key");
        assertThat(report.location().name()).isEqualTo("滨湖区");
        assertThat(report.location().adm2()).isEqualTo("无锡市");
        assertThat(report.temperature()).isEqualTo(31);
        assertThat(report.description()).isEqualTo("多云");
    }

    @Test
    void asksForMoreContextWhenDistrictNameIsAmbiguous() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/geo/v2/city/lookup", exchange -> respond(exchange, """
            {"code":"200","location":[
            {"id":"1","name":"鼓楼区","adm2":"南京市","adm1":"江苏省","country":"中国"},
            {"id":"2","name":"鼓楼区","adm2":"徐州市","adm1":"江苏省","country":"中国"}]}
            """));
        server.start();

        assertThatThrownBy(() -> service().resolveLocation("鼓楼区"))
            .isInstanceOf(WeatherException.class)
            .hasMessageContaining("地区名称存在重复", "南京市", "徐州市");
    }

    @Test
    void queriesForecastForRelativeFutureDate() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/geo/v2/city/lookup", exchange -> respond(exchange, """
            {"code":"200","location":[{"id":"101190202","name":"滨湖区",
            "adm2":"无锡市","adm1":"江苏省","country":"中国"}]}
            """));
        server.createContext("/v7/weather/7d", exchange -> respond(exchange, """
            {"code":"200","updateTime":"2026-07-20T14:00+08:00","daily":[
            {"fxDate":"2026-07-22","tempMax":"32","tempMin":"25",
            "textDay":"多云","textNight":"阵雨","humidity":"70",
            "windDirDay":"东南风","windScaleDay":"3","windSpeedDay":"14",
            "precip":"1.2","uvIndex":"6"}]}
            """));
        server.start();

        var answer = service().weather(
            "无锡滨湖区",
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 22),
            "后天",
            false);

        assertThat(answer.current()).isNull();
        assertThat(answer.forecast().targetDate()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(answer.forecast().maximumTemperature()).isEqualTo(32);
        assertThat(answer.forecast().daytimeDescription()).isEqualTo("多云");
    }

    private WeatherService service() {
        WeatherProperties properties = new WeatherProperties();
        properties.setApiKey("weather-test-key");
        properties.setApiHost("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return new WeatherService(properties, new ObjectMapper(), HttpClient.newHttpClient());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
        throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondGzip(
        com.sun.net.httpserver.HttpExchange exchange,
        String body) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = output.toByteArray();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

package com.example.spring.wechat.map.client;

import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapRouteLeg;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapTransportMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapStaticMapClientTests {

    @Test
    void rendersStaticMapAndAddsRouteLegend() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapStaticMapClient client = new AmapStaticMapClient(
                builder, "test-key", "https://restapi.amap.com", true);
        MapPlace origin = place("杭州东站", "120.212,30.290");
        MapPlace destination = place("西湖断桥", "120.149,30.258");
        MapRouteLeg leg = new MapRouteLeg(origin, destination, new MapRouteOption(
                MapTransportMode.DRIVING,
                12500,
                1800,
                "",
                "0",
                null,
                List.of(),
                "速度优先",
                List.of("沿天城路行驶"),
                List.of("120.212,30.290", "120.180,30.275", "120.149,30.258")));

        server.expect(once(), request -> {
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertThat(request.getURI().getPath()).isEqualTo("/v3/staticmap");
                    assertThat(query).contains("paths=6,0x2F80ED,0.9,,:120.212,30.290;");
                    assertThat(query).doesNotContain("weight:6", "color:0x2F80ED");
                })
                .andRespond(withSuccess(png(800, 600), MediaType.IMAGE_PNG));

        var result = client.renderRoute(
                "杭州东站 → 西湖断桥", List.of(origin, destination), List.of(leg));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/png");
        assertThat(result.orElseThrow().width()).isEqualTo(800);
        assertThat(result.orElseThrow().height()).isGreaterThan(600);
        assertThat(result.orElseThrow().imageBytes()).isNotEmpty();
        server.verify();
    }

    @Test
    void fallsBackToSchematicImageWhenAmapReturnsJsonError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapStaticMapClient client = new AmapStaticMapClient(
                builder, "test-key", "https://restapi.amap.com", true);
        MapPlace origin = place("杭州东站", "120.212,30.290");
        MapPlace destination = place("西湖断桥", "120.149,30.258");
        MapRouteLeg leg = new MapRouteLeg(origin, destination, new MapRouteOption(
                MapTransportMode.DRIVING,
                12500,
                1800,
                "",
                "0",
                null,
                List.of(),
                "速度优先",
                List.of(),
                List.of(origin.location(), destination.location())));

        server.expect(once(), request -> assertThat(request.getURI().getPath()).isEqualTo("/v3/staticmap"))
                .andRespond(withSuccess(
                        "{\"status\":\"0\",\"info\":\"UNKNOWN_ERROR\",\"infocode\":\"20003\"}",
                        MediaType.APPLICATION_JSON));

        var result = client.renderRoute(
                "杭州东站 → 西湖断桥", List.of(origin, destination), List.of(leg));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().imageBytes()).isNotEmpty();
        assertThat(result.orElseThrow().width()).isEqualTo(800);
        assertThat(result.orElseThrow().height()).isGreaterThan(600);
        server.verify();
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(240, 242, 245));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static MapPlace place(String name, String location) {
        return new MapPlace(
                "id-" + name,
                name,
                "",
                "",
                location,
                "浙江省",
                "杭州市",
                "",
                "330100",
                "",
                null,
                "",
                "",
                "",
                List.of());
    }
}

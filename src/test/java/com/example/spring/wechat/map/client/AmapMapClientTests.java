package com.example.spring.wechat.map.client;

import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapServiceException;
import com.example.spring.wechat.map.model.MapTransportMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapMapClientTests {

    @Test
    void searchesPlacesAndMapsBusinessDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapMapClient client = new AmapMapClient(builder, "test-key", "https://restapi.amap.com");

        server.expect(once(), requestTo(allOf(
                        containsString("/v3/place/text"),
                        containsString("keywords=%E8%A5%BF%E6%B9%96"),
                        containsString("city=%E6%9D%AD%E5%B7%9E"),
                        containsString("extensions=all"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "pois": [{
                            "id": "B0FFG12345",
                            "name": "西湖风景名胜区",
                            "type": "风景名胜;风景名胜相关;旅游景点",
                            "address": "龙井路1号",
                            "location": "120.143,30.243",
                            "pname": "浙江省",
                            "cityname": "杭州市",
                            "adname": "西湖区",
                            "adcode": "330106",
                            "tel": "0571-12345678",
                            "biz_ext": {"rating": "4.8", "cost": "0", "open_time": "全天开放"},
                            "photos": [{"url": "https://example.com/west-lake.jpg"}]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MapPlace> places = client.searchPlaces("西湖", "杭州", 5);

        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("西湖风景名胜区");
            assertThat(place.location()).isEqualTo("120.143,30.243");
            assertThat(place.rating()).isEqualTo("4.8");
            assertThat(place.openingHours()).isEqualTo("全天开放");
            assertThat(place.isAttraction()).isTrue();
        });
        server.verify();
    }

    @Test
    void mapsDrivingRouteDistanceDurationAndTolls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapMapClient client = new AmapMapClient(builder, "test-key", "https://restapi.amap.com");
        MapPlace origin = place("杭州东站", "120.212,30.290");
        MapPlace destination = place("西湖断桥", "120.149,30.258");

        server.expect(once(), requestTo(allOf(
                        containsString("/v3/direction/driving"),
                        containsString("origin=120.212,30.290"),
                        containsString("destination=120.149,30.258"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "route": {
                            "paths": [{
                              "distance": "12500",
                              "duration": "1800",
                              "strategy": "速度优先",
                              "tolls": "0"
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MapRouteOption> routes = client.planRoutes(origin, destination, MapTransportMode.DRIVING);

        assertThat(routes).containsExactly(new MapRouteOption(
                MapTransportMode.DRIVING,
                12500,
                1800,
                "",
                "0",
                null,
                List.of(),
                "速度优先"));
        server.verify();
    }

    @Test
    void mapsTransitAlternativesAndLineNames() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapMapClient client = new AmapMapClient(builder, "test-key", "https://restapi.amap.com");
        MapPlace origin = place("杭州东站", "120.212,30.290");
        MapPlace destination = place("西湖断桥", "120.149,30.258");

        server.expect(once(), requestTo(allOf(
                        containsString("/v3/direction/transit/integrated"),
                        containsString("city=330100"),
                        containsString("cityd=330100"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "route": {
                            "transits": [{
                              "distance": "14100",
                              "duration": "3000",
                              "cost": "4",
                              "walking_distance": "800",
                              "segments": [
                                {"bus": {"buslines": [{"name": "地铁1号线"}]}},
                                {"bus": {"buslines": [{"name": "地铁2号线"}]}}
                              ]
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MapRouteOption> routes = client.planRoutes(origin, destination, MapTransportMode.TRANSIT);

        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route.mode()).isEqualTo(MapTransportMode.TRANSIT);
            assertThat(route.durationSeconds()).isEqualTo(3000);
            assertThat(route.walkingDistanceMeters()).isEqualTo(800);
            assertThat(route.transitLines()).containsExactly("地铁1号线", "地铁2号线");
            assertThat(route.summary()).isEqualTo("地铁1号线 → 地铁2号线");
        });
        server.verify();
    }

    @Test
    void searchesNearbyWithAmapCategoryAndDistance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapMapClient client = new AmapMapClient(builder, "test-key", "https://restapi.amap.com");

        server.expect(once(), requestTo(allOf(
                        containsString("/v3/place/around"),
                        containsString("types=050000"),
                        containsString("radius=3000"),
                        containsString("sortrule=distance"))))
                .andRespond(withSuccess("""
                        {
                          "status": "1",
                          "info": "OK",
                          "pois": [{
                            "id": "food-1",
                            "name": "湖滨餐厅",
                            "type": "餐饮服务;中餐厅",
                            "address": "湖滨路1号",
                            "location": "120.150,30.250",
                            "distance": "650",
                            "biz_ext": {"rating": "4.6", "cost": "85"}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MapPlace> places = client.searchNearby(
                place("西湖", "120.143,30.243"),
                MapNearbyCategory.FOOD,
                3000,
                5);

        assertThat(places).singleElement().satisfies(result -> {
            assertThat(result.name()).isEqualTo("湖滨餐厅");
            assertThat(result.distanceMeters()).isEqualTo(650);
            assertThat(result.averageCost()).isEqualTo("85");
        });
        server.verify();
    }

    @Test
    void rejectsMissingMapKeyBeforeCallingRemoteService() {
        AmapMapClient client = new AmapMapClient(
                RestClient.builder(),
                "",
                "https://restapi.amap.com");

        assertThatThrownBy(() -> client.searchPlaces("西湖", "杭州", 5))
                .isInstanceOf(MapServiceException.class)
                .hasMessage("未配置高德地图 Web 服务 KEY");
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

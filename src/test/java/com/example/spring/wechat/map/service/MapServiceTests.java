package com.example.spring.wechat.map.service;

import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.client.MapStaticImageClient;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.map.model.MapMultiRouteResult;
import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapOrderPolicy;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapResult;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapTransportMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapServiceTests {

    @Test
    void addsMapAndTicketSearchLinksForNearbyAttractions() {
        FakeMapClient client = new FakeMapClient();
        MapService service = new MapService(client);

        MapResult result = service.searchNearby(
                "杭州西湖",
                "杭州",
                MapNearbyCategory.ATTRACTION,
                3000);

        assertThat(result.places()).extracting(MapPlace::name).containsExactly("雷峰塔");
        assertThat(result.links())
                .extracting(link -> link.label())
                .anyMatch(label -> label.contains("高德地图"))
                .anyMatch(label -> label.contains("携程"));
        assertThat(result.notices()).singleElement().asString().contains("以平台页面为准");
        assertThat(client.lastCategory).isEqualTo(MapNearbyCategory.ATTRACTION);
        assertThat(client.lastRadius).isEqualTo(3000);
    }

    @Test
    void plansEveryLegAndBuildsACompleteMultiRouteResult() {
        FakeMapClient client = new FakeMapClient();
        MapStaticImageClient imageClient = (title, places, legs) -> java.util.Optional.of(
                new ImageGenerationResult("route-map", "", new byte[]{1}, "route-map.png", "image/png", 800, 700));
        MapService service = new MapService(client, imageClient);

        MapMultiRouteResult result = service.planMultiRoute(
                List.of("杭州东站", "西湖断桥", "灵隐寺"),
                "杭州",
                MapTransportMode.DRIVING,
                MapOrderPolicy.PRESERVE,
                true,
                false,
                true);

        assertThat(result.orderedPlaces()).extracting(MapPlace::name)
                .containsExactly("杭州东站", "西湖断桥", "灵隐寺");
        assertThat(result.legs()).hasSize(2);
        assertThat(result.totalDistanceMeters()).isEqualTo(2000);
        assertThat(result.totalDurationSeconds()).isEqualTo(1200);
        assertThat(result.image()).isNotNull();
        assertThat(client.routeCalls).isEqualTo(2);
    }

    @Test
    void prefersCanonicalLandmarksOverRelatedPoisWithoutFalseAmbiguity() {
        FakeMapClient client = new FakeMapClient();
        MapService service = new MapService(client);

        MapMultiRouteResult result = service.planMultiRoute(
                List.of("杭州东站", "断桥残雪", "宋城"),
                "杭州",
                MapTransportMode.DRIVING,
                MapOrderPolicy.PRESERVE,
                true,
                false,
                false);

        assertThat(result.orderedPlaces()).extracting(MapPlace::name)
                .containsExactly("杭州东站", "杭州西湖风景名胜区-断桥残雪", "杭州宋城");
        assertThat(result.legs()).hasSize(2);
    }

    private static final class FakeMapClient implements MapClient {
        private MapNearbyCategory lastCategory;
        private int lastRadius;
        private int routeCalls;

        @Override
        public List<MapPlace> searchPlaces(String query, String city, int limit) {
            if ("断桥残雪".equals(query)) {
                return List.of(
                        place("杭州西湖风景名胜区-断桥残雪", "风景名胜;旅游景点", "120.151,30.258", null),
                        place("杭州西湖风景名胜区-断桥残雪互动频点", "科教文化服务", "120.152,30.258", null));
            }
            if ("宋城".equals(query)) {
                return List.of(
                        place("杭州宋城", "风景名胜;旅游景点", "120.099,30.170", null),
                        place("宋城(地铁站)", "交通设施服务;地铁站", "120.102,30.169", null));
            }
            return List.of(switch (query) {
                case "杭州东站" -> place("杭州东站", "交通设施服务", "120.212,30.290", null);
                case "西湖断桥" -> place("西湖断桥", "风景名胜;旅游景点", "120.149,30.258", null);
                case "灵隐寺" -> place("灵隐寺", "风景名胜;旅游景点", "120.102,30.240", null);
                default -> place("西湖风景名胜区", "风景名胜;旅游景点", "120.143,30.243", null);
            });
        }

        @Override
        public MapPlace placeDetail(String placeId) {
            return place("西湖风景名胜区", "风景名胜;旅游景点", "120.143,30.243", null);
        }

        @Override
        public List<MapPlace> searchNearby(
                MapPlace center,
                MapNearbyCategory category,
                int radiusMeters,
                int limit) {
            this.lastCategory = category;
            this.lastRadius = radiusMeters;
            return List.of(place("雷峰塔", "风景名胜;旅游景点", "120.149,30.233", 1200));
        }

        @Override
        public List<MapRouteOption> planRoutes(
                MapPlace origin,
                MapPlace destination,
                MapTransportMode mode) {
            routeCalls++;
            return List.of(new MapRouteOption(
                    mode,
                    1000,
                    600,
                    "",
                    "0",
                    null,
                    List.of(),
                    "测试路线",
                    List.of("直行"),
                    List.of(origin.location(), destination.location())));
        }

        private MapPlace place(String name, String type, String location, Integer distance) {
            return new MapPlace(
                    "id-" + name,
                    name,
                    type,
                    "南山路",
                    location,
                    "浙江省",
                    "杭州市",
                    "西湖区",
                    "330106",
                    "",
                    distance,
                    "4.7",
                    "40",
                    "08:00-17:30",
                    List.of());
        }
    }
}

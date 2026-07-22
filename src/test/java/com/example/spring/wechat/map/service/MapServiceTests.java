package com.example.spring.wechat.map.service;

import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.model.MapNearbyCategory;
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

    private static final class FakeMapClient implements MapClient {
        private MapNearbyCategory lastCategory;
        private int lastRadius;

        @Override
        public List<MapPlace> searchPlaces(String query, String city, int limit) {
            return List.of(place("西湖风景名胜区", "风景名胜;旅游景点", "120.143,30.243", null));
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
            return List.of();
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

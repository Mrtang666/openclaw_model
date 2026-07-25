package com.example.spring.wechat.taxi.service;

import com.example.spring.wechat.taxi.client.DidiTaxiGateway;
import com.example.spring.wechat.taxi.repository.RideRepository;
import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.taxi.model.RideOrder;
import com.example.spring.wechat.taxi.model.RideOrderStatus;
import com.example.spring.wechat.taxi.model.RideQuote;
import com.example.spring.wechat.taxi.model.RideQuoteOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RideOrchestrationServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DidiTaxiGateway didi = mock(DidiTaxiGateway.class);
    private final RideRepository repository = mock(RideRepository.class);
    private final MapClient mapClient = mock(MapClient.class);
    private final RideOrchestrationService service = new RideOrchestrationService(didi, repository, mapClient);

    @Test
    void missingLocationTerminatesWithoutStackOverflow() throws Exception {
        when(didi.textSearch(anyString(), anyString())).thenReturn(mapper.readTree("{\"data\":{\"pois\":[]}}"));
        assertThatThrownBy(() -> service.estimate("s", "未知起点", "未知终点", "杭州"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("坐标");
    }

    @Test
    void findsLocationInNestedPoiResponse() throws Exception {
        when(didi.textSearch(eq("起点"), anyString())).thenReturn(mapper.readTree("{\"data\":{\"pois\":[{\"location\":\"120.1,30.1\"}]}}"));
        when(didi.textSearch(eq("终点"), anyString())).thenReturn(mapper.readTree("{\"result\":{\"pois\":[{\"location\":\"120.2,30.2\"}]}}"));
        when(didi.estimate(anyMap())).thenReturn(mapper.readTree("{\"estimate_trace_id\":\"trace-1\"}"));
        when(didi.raw(any())).thenReturn("{}");
        when(didi.parseOptions(any(), anyString())).thenReturn(List.of());

        service.estimate("s", "起点", "终点", "杭州");

        verify(repository).saveQuote(argThat(q -> q.originLocation().equals("120.1,30.1")
                && q.destinationLocation().equals("120.2,30.2")), eq("{}"));
    }

    @Test
    void preparesResolvedLocationsForUserConfirmation() {
        when(mapClient.searchPlaces(eq("阿里巴巴高桥云港园区"), eq("杭州"), eq(5)))
                .thenReturn(List.of(place("阿里巴巴高桥云港园区", "余杭区万和路", "120.01,30.01")));
        when(mapClient.searchPlaces(eq("杭州西湖"), eq("杭州"), eq(8)))
                .thenReturn(List.of(place("杭州西湖风景名胜区", "西湖区龙井路1号", "120.02,30.02")));

        service.prepareLocationConfirmation("session", "阿里巴巴高桥云港园区", "杭州西湖", "杭州");

        verify(repository).saveLocationConfirmation(argThat(c -> c.originAddress().contains("万和路")
                && c.destinationName().contains("西湖") && c.sessionKey().equals("session")));
    }

    @Test
    void asksForMoreSpecificPlaceWhenNoCandidateExists() {
        when(mapClient.searchPlaces(anyString(), anyString(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> service.prepareLocationConfirmation("session", "园区", "西湖", "杭州"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("补充");
    }

    @Test
    void broadScenicDestinationRequiresConcreteDropOffPoint() {
        when(mapClient.searchPlaces(eq("高桥云港园区7号门"), eq("杭州"), eq(5)))
                .thenReturn(List.of(place("高桥云港园区-7号门", "万和路", "120.02,30.31")));
        MapPlace broad = new MapPlace("west-lake", "杭州西湖风景名胜区", "风景名胜;国家级景点", "龙井路1号", "120.121358,30.222692", "浙江", "杭州", "西湖区", "", "", null, "", "", "", List.of());
        MapPlace park = new MapPlace("park", "杭州西湖风景名胜区-湖滨公园", "风景名胜;公园", "湖滨路", "120.158818,30.256583", "浙江", "杭州", "西湖区", "", "", null, "", "", "", List.of());
        when(mapClient.searchPlaces(eq("杭州西湖风景名胜区"), eq("杭州"), eq(8))).thenReturn(List.of(broad, park));

        assertThatThrownBy(() -> service.prepareLocationConfirmation("session", "高桥云港园区7号门", "杭州西湖风景名胜区", "杭州"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("范围较大", "湖滨公园", "具体地点");
        verify(repository, never()).saveLocationConfirmation(any());
    }

    @Test
    void cancellationRequiresCancellableLatestStatus() throws Exception {
        RideOrder order = order(RideOrderStatus.IN_TRIP);
        when(repository.findOrder("order-1")).thenReturn(order);
        when(didi.queryOrder("order-1")).thenReturn(mapper.readTree("{\"status\":\"in_trip\"}"));
        when(didi.raw(any())).thenReturn("{}");

        assertThatThrownBy(() -> service.prepareCancellation("session", "order-1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不能取消");
        verify(didi, never()).cancelOrder(anyString(), anyString());
    }

    @Test
    void cancellationCallsDidiOnlyAfterConfirmationPath() throws Exception {
        RideOrder order = order(RideOrderStatus.DRIVER_SEARCHING);
        when(repository.findOrder("order-1")).thenReturn(order);
        when(didi.queryOrder("order-1")).thenReturn(mapper.readTree("{}"));
        when(didi.cancelOrder("order-1", "用户取消")).thenReturn(mapper.readTree("{\"success\":true}"));
        when(didi.raw(any())).thenReturn("{}");

        service.cancel("session", "order-1", "用户取消");

        verify(didi).cancelOrder("order-1", "用户取消");
        verify(repository, atLeastOnce()).saveOrder(argThat(o -> o.status() == RideOrderStatus.CANCELLED));
    }

    @Test
    void directOrderRequiresPhoneMatchingDidiAccount() {
        when(repository.findQuote("quote-1")).thenReturn(quote());
        assertThatThrownBy(() -> service.confirm("session", "quote-1", 1, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("手机号");
        verify(didi, never()).createOrder(anyString(), anyString(), anyString());
    }

    @Test
    void generatesAppLinkAsAlternativeToDirectOrder() throws Exception {
        when(repository.findQuote("quote-1")).thenReturn(quote());
        when(didi.generateRideAppLink(anyMap())).thenReturn(mapper.readTree("{\"appLink\":\"onetravel://ride\",\"miniprogramLink\":\"https://v.didi.cn/p/test?fromlat=30.1&tolat=30.2\",\"browserLink\":\"https://v.didi.cn/test\"}"));
        var link = service.generateRideAppLink("session", "quote-1", 1);
        assertThat(link.browserLink()).isEqualTo("https://v.didi.cn/test");
        assertThat(link.miniProgramLink()).contains("fromlat=30.1", "tolat=30.2");
        verify(didi).generateRideAppLink(argThat(args -> "1".equals(args.get("product_category"))));
    }

    private MapPlace place(String name, String address, String location) {
        return new MapPlace("id", name, "", address, location, "浙江省", "杭州市", "", "", "", null, "", "", "", List.of());
    }

    private RideOrder order(RideOrderStatus status) {
        return new RideOrder("order-1", "session", "quote-1", "1", status, "", "", "", null, null, "{}", null);
    }

    private RideQuote quote() {
        return new RideQuote("quote-1", "session", "起点", "120.1,30.1", "终点", "120.2,30.2", "trace-1",
                List.of(new RideQuoteOption("1", "1", "快车", new java.math.BigDecimal("40"), new java.math.BigDecimal("40"), null, "{}")), Instant.now().plusSeconds(300));
    }
}

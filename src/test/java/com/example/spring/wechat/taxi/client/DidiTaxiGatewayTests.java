package com.example.spring.wechat.taxi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DidiTaxiGatewayTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DidiTaxiGateway gateway = new DidiTaxiGateway(mock(DidiMcpClient.class), mapper);

    @Test
    void parsesCurrentDidiStructuredEstimateResponse() throws Exception {
        var response = mapper.readTree("""
                {"traceId":"trace-1","items":[
                  {"index":1,"productName":"惊喜特价","productCategory":"191","priceText":"24"},
                  {"index":2,"productName":"特惠快车","productCategory":"201","priceText":"37","priceDiscounted":"27"}
                ]}
                """);

        var options = gateway.parseOptions(response, response.toString());

        assertThat(options).hasSize(2);
        assertThat(options.get(0).name()).isEqualTo("惊喜特价");
        assertThat(options.get(0).productCategory()).isEqualTo("191");
        assertThat(options.get(0).minPrice()).isEqualByComparingTo("24");
        assertThat(options.get(1).minPrice()).isEqualByComparingTo("27");
        assertThat(options.get(1).maxPrice()).isEqualByComparingTo("37");
    }

    @Test
    void findsItemsInNestedResponse() throws Exception {
        var response = mapper.readTree("{\"data\":{\"result\":{\"items\":[{\"productName\":\"快车\",\"productCategory\":\"1\",\"priceText\":\"41\"}]}}}");
        assertThat(gateway.parseOptions(response, response.toString())).singleElement()
                .satisfies(option -> assertThat(option.name()).isEqualTo("快车"));
    }
}

package com.example.spring.wechat.food.service;

import com.example.spring.wechat.food.gateway.FoodDeliveryGateway;
import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderItem;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodOrderStatus;
import com.example.spring.wechat.food.model.StoredFoodOrderPreview;
import com.example.spring.wechat.food.repository.FoodDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodOrderOrchestrationServiceTests {

    private final FoodDeliveryRepository repository = mock(FoodDeliveryRepository.class);
    private final FoodDeliveryGateway gateway = mock(FoodDeliveryGateway.class);
    private final FoodOrderOrchestrationService service = new FoodOrderOrchestrationService(repository, gateway);

    @Test
    void refusesToSaveAddressWithoutConsent() {
        assertThatThrownBy(() -> service.saveAddress(
                "user-1", "", "家", "张先生", "13800138000",
                "杭州", "滨江区", "星光大道3幢1202", "", "", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("明确同意");

        verify(repository, never()).saveAddress(any());
    }

    @Test
    void previewCreatesOneTimeConfirmationToken() {
        FoodDeliveryAddress address = address();
        FoodOrderDraft draft = draft();
        when(repository.findDraft("user-1")).thenReturn(Optional.of(draft));
        when(repository.findAddress("user-1", "address-1")).thenReturn(Optional.of(address));
        when(gateway.preview(address, draft)).thenReturn(preview("provider-preview", ""));
        when(repository.savePreview(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        FoodOrderPreview result = service.preview("user-1");

        assertThat(result.previewId()).isEqualTo("provider-preview");
        assertThat(result.confirmationToken()).isNotBlank();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).savePreview(any(), hash.capture());
        assertThat(hash.getValue()).hasSize(64).doesNotContain(result.confirmationToken());
    }

    @Test
    void requiresExplicitConfirmationBeforeCreatingOrder() {
        assertThatThrownBy(() -> service.confirmOrder("user-1", "preview-1", "token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("确认下单");

        verify(gateway, never()).createOrder(any());
    }

    @Test
    void rejectsExpiredPreview() {
        FoodOrderPreview expired = new FoodOrderPreview(
                "preview-1", "user-1", "address-1", "merchant-1", "门店",
                draft().items(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, 30, "", Instant.now().minusSeconds(1), "{}");
        when(repository.findPreview("preview-1"))
                .thenReturn(Optional.of(new StoredFoodOrderPreview(expired, "ignored")));

        assertThatThrownBy(() -> service.confirmOrder("user-1", "preview-1", "token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("价格已经过期");
    }

    private FoodDeliveryAddress address() {
        return new FoodDeliveryAddress("address-1", "user-1", "家", "张先生", "13800138000",
                "杭州", "滨江区", "星光大道3幢1202", "120.1", "30.2", true, Instant.now());
    }

    private FoodOrderDraft draft() {
        return new FoodOrderDraft("user-1", "address-1", "merchant-1", "示例门店",
                List.of(new FoodOrderItem("product-1", "sku-1", "鸡腿堡", "中份", 1, BigDecimal.TEN)),
                "不要餐具", Instant.now());
    }

    private FoodOrderPreview preview(String previewId, String token) {
        return new FoodOrderPreview(previewId, "user-1", "address-1", "merchant-1", "示例门店",
                draft().items(), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("12"), 30, token, Instant.now().plusSeconds(300), "{}");
    }
}

package com.core.beautyshop.modules.order.application.mapper;

import com.core.beautyshop.modules.order.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void shippingAddressOmitsMissingOptionalParts() {
        Order order = Order.builder()
                .shippingAddress("123 Nguyễn Huệ")
                .ward(null)
                .district("")
                .city("TP. Hồ Chí Minh")
                .items(new ArrayList<>())
                .build();

        var response = mapper.toOrderResponse(order);

        assertEquals("123 Nguyễn Huệ, TP. Hồ Chí Minh", response.getShippingAddress());
    }

    @Test
    void spaPackageIdIsExposedInOrderContract() {
        Order order = Order.builder()
                .servicePackageId(42L)
                .shippingAddress("SPA_SERVICE")
                .items(new ArrayList<>())
                .build();

        var response = mapper.toOrderResponse(order);

        assertEquals(42L, response.getServicePackageId());
        assertEquals("SPA_SERVICE", response.getShippingAddress());
    }
}

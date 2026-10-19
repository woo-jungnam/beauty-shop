package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.Order;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProductReturnAmountsTest {
    @Test void returnsDiscountedProductValueWithoutRefundingShippingOrExceedingPaidProductObligation(){
        var order=Order.builder().subTotal(new BigDecimal("200000")).totalAmount(new BigDecimal("175000")).shippingFee(new BigDecimal("25000")).build();
        assertEquals(new BigDecimal("75000"),ProductReturnService.proratedProductRefund(order,new BigDecimal("100000")));
        assertEquals(new BigDecimal("150000"),ProductReturnService.proratedProductRefund(order,new BigDecimal("200000")));
    }
}

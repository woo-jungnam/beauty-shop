package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.application.dto.response.ShipperOrderView;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.application.dto.response.ShipperStatsView;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ShipperOrderService {
    Page<ShipperOrderView> getShipperOrders(Long shipperId, OrderStatus status, Pageable pageable);

    ShipperOrderView getShipperOrderDetail(Long orderId, Long shipperId);

    ShipperOrderView startDelivery(Long orderId, Long shipperId);

    ShipperOrderView completeDelivery(Long orderId, Long shipperId, MultipartFile proofImage, String notes);

    ShipperOrderView reportDeliveryFailure(Long orderId, Long shipperId, String reason);

    Page<ShipperOrderView> getDeliveryHistory(Long shipperId, LocalDate from, LocalDate to, OrderStatus status, Pageable pageable);

    ShipperStatsView getShipperStats(Long shipperId, LocalDate from, LocalDate to);
}


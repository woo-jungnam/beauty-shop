package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.application.dto.response.ShipperReturnView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ShipperReturnService {
    Page<ShipperReturnView> getShipperReturns(Long shipperId, String status, String keyword, Pageable pageable);

    ShipperReturnView getShipperReturnDetail(Long returnId, Long shipperId);

    ShipperReturnView startPickup(Long returnId, Long shipperId);

    ShipperReturnView completePickup(Long returnId, Long shipperId, MultipartFile proofImage, String notes);

    ShipperReturnView reportPickupFailure(Long returnId, Long shipperId, String reason);

    ShipperReturnView requestHandover(Long returnId, Long shipperId, String notes);
}


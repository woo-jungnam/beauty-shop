package com.core.beautyshop.modules.order.api.dto;

import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import java.util.List;

public record CreateSpaVisitOrderCommand(Long appointmentId, Long userId, List<SpaVisitCharge> items,
                                        PaymentMethod paymentMethod, String customerName, String customerPhone,
                                        String notes, String idempotencyKey) { }

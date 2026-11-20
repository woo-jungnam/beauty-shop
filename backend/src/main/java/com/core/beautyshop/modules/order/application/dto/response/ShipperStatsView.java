package com.core.beautyshop.modules.order.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ShipperStatsView(long totalOrders, long deliveredOrders, BigDecimal totalCodCollected,
                               BigDecimal totalRevenue, BigDecimal totalDeliveryFees, BigDecimal estimatedEarnings,
                               long todayOrders,
                               long weekOrders, long monthOrders, Map<LocalDate, Long> ordersByDay) {}

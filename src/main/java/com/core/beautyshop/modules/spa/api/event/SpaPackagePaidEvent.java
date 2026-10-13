package com.core.beautyshop.modules.spa.api.event;

public record SpaPackagePaidEvent(Long orderId, Long userId, Long packageId) {
}

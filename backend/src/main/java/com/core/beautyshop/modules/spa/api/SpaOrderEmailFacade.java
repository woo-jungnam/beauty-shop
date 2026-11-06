package com.core.beautyshop.modules.spa.api;

import java.util.List;
import java.util.Optional;

public interface SpaOrderEmailFacade {
    Optional<PackageDetail> findPurchasedPackage(Long orderId);
    record PackageDetail(String name, Integer validityDays, List<Entitlement> entitlements) {}
    record Entitlement(String serviceName, int sessions) {}
}

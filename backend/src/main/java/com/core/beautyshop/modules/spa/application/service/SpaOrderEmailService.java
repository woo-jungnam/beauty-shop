package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.api.SpaOrderEmailFacade;
import com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpaOrderEmailService implements SpaOrderEmailFacade {
    private final SpaPurchaseSnapshotRepository snapshots;

    @Override
    @Transactional(readOnly = true)
    public Optional<PackageDetail> findPurchasedPackage(Long orderId) {
        if (orderId == null) return Optional.empty();
        return snapshots.findById(orderId).map(snapshot -> new PackageDetail(snapshot.getPackageName(), snapshot.getValidityDays(),
                snapshot.getEntitlements().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                    var detail = snapshot.getServiceDetails().get(entry.getKey());
                    return new Entitlement(detail == null ? "Dịch vụ #" + entry.getKey() : detail.getName(), entry.getValue());
                }).toList()));
    }
}

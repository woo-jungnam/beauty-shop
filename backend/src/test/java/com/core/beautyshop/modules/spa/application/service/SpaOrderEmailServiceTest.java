package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshot;
import com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshotRepository;
import com.core.beautyshop.modules.spa.domain.PurchasedServiceDetail;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpaOrderEmailServiceTest {
    @Test
    void usesPurchasedNamesAndSessionCountsInsteadOfCurrentCatalog() {
        var repository = mock(SpaPurchaseSnapshotRepository.class);
        var snapshot = new SpaPurchaseSnapshot();
        snapshot.setPackageName("Gói phục hồi da lúc mua");
        snapshot.setValidityDays(90);
        snapshot.setEntitlements(Map.of(3L, 6));
        snapshot.setServiceDetails(Map.of(3L, new PurchasedServiceDetail("Chăm sóc chuyên sâu", BigDecimal.TEN)));
        when(repository.findById(21L)).thenReturn(Optional.of(snapshot));
        var detail = new SpaOrderEmailService(repository).findPurchasedPackage(21L).orElseThrow();
        assertThat(detail.name()).isEqualTo("Gói phục hồi da lúc mua");
        assertThat(detail.validityDays()).isEqualTo(90);
        assertThat(detail.entitlements().getFirst().serviceName()).isEqualTo("Chăm sóc chuyên sâu");
        assertThat(detail.entitlements().getFirst().sessions()).isEqualTo(6);
    }
}

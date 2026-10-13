package com.core.beautyshop.modules.spa.application.service;

import io.swagger.v3.oas.annotations.media.Schema;

/** Default policy: settle a completed visit; no deposits, cancellation fees or no-show fees. */
@Schema(description = "Quy tắc checkout mặc định; chưa bật cọc/phí hủy/no-show hoặc tự hoàn mục SKIPPED")
public record SpaCheckoutPolicy(@Schema(example = "COMPLETED") String invoiceStage,
                                @Schema(example = "PERFORMED_WITHOUT_TICKET") String chargeableItems,
                                @Schema(example = "VND") String currency,
                                @Schema(example = "HALF_UP_ON_INVOICE_TOTAL") String rounding,
                                boolean depositsEnabled, boolean cancellationFeesEnabled, boolean noShowFeesEnabled,
                                boolean automaticallyRefundSkippedServices) {
    public static SpaCheckoutPolicy defaults() {
        return new SpaCheckoutPolicy("COMPLETED", "PERFORMED_WITHOUT_TICKET", "VND", "HALF_UP_ON_INVOICE_TOTAL",
                false, false, false, false);
    }
}

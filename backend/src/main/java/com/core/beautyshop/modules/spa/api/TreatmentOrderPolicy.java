package com.core.beautyshop.modules.spa.api;

/** Read-only boundary used by order workflows without acquiring course locks after order locks. */
public interface TreatmentOrderPolicy {
    boolean requiresTreatmentWorkflow(Long orderId);
}

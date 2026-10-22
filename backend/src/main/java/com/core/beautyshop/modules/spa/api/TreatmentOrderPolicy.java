package com.core.beautyshop.modules.spa.api;

public interface TreatmentOrderPolicy {
    boolean requiresTreatmentWorkflow(Long orderId);
}

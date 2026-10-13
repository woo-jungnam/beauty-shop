package com.core.beautyshop.modules.crm.api;

import java.time.Instant;
import java.util.List;

/** Internal module API: caller must first authorize access to this customer's appointment. */
public interface CustomerCareFacade {
    List<CareNote> getCareNotes(Long customerId);
    record CareNote(Long id, String noteType, String content, String skinProfile, String allergies,
                    String contraindications, Instant followUpAt, Instant updatedAt) {}
}

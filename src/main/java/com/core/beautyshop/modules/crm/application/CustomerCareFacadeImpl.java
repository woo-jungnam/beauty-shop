package com.core.beautyshop.modules.crm.application;

import com.core.beautyshop.modules.crm.api.CustomerCareFacade;
import com.core.beautyshop.modules.crm.domain.CustomerCareNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerCareFacadeImpl implements CustomerCareFacade {
    private final CustomerCareNoteRepository notes;

    @Override
    @Transactional(readOnly = true)
    public List<CareNote> getCareNotes(Long customerId) {
        return notes.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(customerId).stream()
                .map(note -> new CareNote(note.getId(), note.getNoteType(), note.getContent(), note.getSkinProfile(),
                        note.getAllergies(), note.getContraindications(), note.getFollowUpAt(), note.getUpdatedAt()))
                .toList();
    }
}

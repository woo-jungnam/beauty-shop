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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<CareNote> getCareNotes(Long customerId) {
        var result = new java.util.ArrayList<>(notes.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(customerId).stream()
                .map(note -> new CareNote(note.getId(), note.getNoteType(), note.getContent(), note.getSkinProfile(),
                        note.getAllergies(), note.getContraindications(), note.getFollowUpAt(), note.getUpdatedAt()))
                .toList());
        result.addAll(jdbc.query("select skin_type,sensitivity,allergies,contraindications,updated_at from customer_skin_profiles where user_id=?",
                (rs,n)->new CareNote(-customerId,"CUSTOMER_DECLARATION","Thông tin khách hàng tự khai; cần được chuyên viên xác nhận khi phục vụ",
                        java.util.stream.Stream.of(rs.getString("skin_type"),rs.getString("sensitivity")).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(", ")),
                        rs.getString("allergies"),rs.getString("contraindications"),null,rs.getTimestamp("updated_at").toInstant()),customerId));
        return result;
    }
}

package com.core.beautyshop.modules.crm.application;

import com.core.beautyshop.modules.crm.domain.CustomerCareNote;
import com.core.beautyshop.modules.crm.domain.CustomerCareNoteRepository;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
@RequiredArgsConstructor
public class CustomerCareService {
    private final JdbcTemplate jdbc;
    private final CustomerCareNoteRepository notes;
    private static final String CUSTOMER_COLUMNS = """
        SELECT u.id,u.username,u.full_name,u.email,u.phone,u.membership_tier,u.loyalty_points,u.status,
          (SELECT COUNT(*) FROM orders o WHERE o.user_id=u.id AND o.is_deleted=false) order_count,
          (SELECT COALESCE(SUM(o.paid_amount),0) FROM orders o
             WHERE o.user_id=u.id AND o.is_deleted=false AND o.payment_status='PAID') lifetime_value,
          (SELECT COUNT(*) FROM appointments a WHERE a.user_id=u.id AND a.is_deleted=false) appointment_count
        FROM users u
        """;
    private static final String SEARCH_FILTER = """
        WHERE u.is_deleted=false AND (LOWER(u.full_name) LIKE LOWER(?) ESCAPE '!'
          OR LOWER(u.username) LIKE LOWER(?) ESCAPE '!' OR u.phone LIKE ? ESCAPE '!'
          OR LOWER(u.email) LIKE LOWER(?) ESCAPE '!')
        """;
    private static final RowMapper<CustomerView> CUSTOMER_MAPPER = (rs, row) -> new CustomerView(rs.getLong("id"),
            rs.getString("username"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
            rs.getString("membership_tier"), rs.getInt("loyalty_points"), rs.getString("status"),
            rs.getLong("order_count"), rs.getBigDecimal("lifetime_value"), rs.getLong("appointment_count"));

    @Transactional(readOnly = true)
    public List<CustomerView> search(String keyword) { return searchPage(keyword, 0, 100).getContent(); }

    @Transactional(readOnly = true)
    public PageResponse<CustomerView> searchPage(String keyword, int page, int size) {
        PageRequest paging = paging(page, size);
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 100) throw new BusinessException("Search keyword must not exceed 100 characters");
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u " + SEARCH_FILTER, Long.class, pattern, pattern, pattern, pattern);
        List<CustomerView> content = jdbc.query(CUSTOMER_COLUMNS + SEARCH_FILTER
                        + " ORDER BY lifetime_value DESC,u.id DESC LIMIT ? OFFSET ?", CUSTOMER_MAPPER,
                pattern, pattern, pattern, pattern, size, paging.getOffset());
        return PageResponse.of(new PageImpl<>(content, paging, total == null ? 0 : total));
    }

    @Transactional(readOnly = true)
    public CustomerView getCustomer(Long userId) {
        List<CustomerView> found = jdbc.query(CUSTOMER_COLUMNS + " WHERE u.is_deleted=false AND u.id=?", CUSTOMER_MAPPER, userId);
        if (found.isEmpty()) throw new ResourceNotFoundException("Customer not found: " + userId);
        return found.get(0);
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(Long userId) { return notesPage(userId, 0, 100).getContent(); }

    @Transactional(readOnly = true)
    public PageResponse<NoteView> notesPage(Long userId, int page, int size) {
        PageRequest paging = paging(page, size);
        requireCustomer(userId);
        return PageResponse.of(notes.findByUserIdAndIsDeletedFalse(userId,
                paging.withSort(Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(this::view));
    }

    @Transactional
    public void deleteNote(Long noteId) {
        CustomerCareNote note = notes.findById(noteId).filter(n -> !Boolean.TRUE.equals(n.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Care note not found: " + noteId));
        note.setIsDeleted(true);
    }

    @Transactional
    public NoteView add(Long userId, NoteCommand command) {
        requireCustomer(userId);
        if (command == null || command.content() == null || command.content().isBlank() || command.content().length() > 10000) {
            throw new BusinessException("Care note content must contain 1 to 10000 characters");
        }
        String type = command.noteType() == null ? "GENERAL" : command.noteType().trim();
        if (type.isBlank() || type.length() > 30) throw new BusinessException("Note type must contain 1 to 30 characters");
        validateProfile(command.skinProfile()); validateProfile(command.allergies()); validateProfile(command.contraindications());
        CustomerCareNote note = CustomerCareNote.builder().userId(userId).noteType(type)
                .content(command.content().trim()).skinProfile(command.skinProfile()).allergies(command.allergies())
                .contraindications(command.contraindications()).followUpAt(command.followUpAt()).build();
        return view(notes.save(note));
    }

    private PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException("Page must be nonnegative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    private void requireCustomer(Long userId) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND is_deleted=false", Integer.class, userId);
        if (exists == null || exists == 0) throw new ResourceNotFoundException("Customer not found");
    }

    private void validateProfile(String value) {
        if (value != null && value.length() > 1000) throw new BusinessException("Care profile fields must not exceed 1000 characters");
    }

    private NoteView view(CustomerCareNote note) {
        return new NoteView(note.getId(), note.getUserId(), note.getNoteType(), note.getContent(), note.getSkinProfile(),
                note.getAllergies(), note.getContraindications(), note.getFollowUpAt(), note.getCreatedAt(), note.getCreatedBy());
    }
    @Schema(name = "CrmCustomerView", description = "Thông tin khách và tổng hợp hoạt động; không kèm private care notes")
    public record CustomerView(Long id,String username,String fullName,String email,String phone,String membershipTier,Integer loyaltyPoints,String status,long orderCount,
            @Schema(description = "Tổng paidAmount của orders hiện có paymentStatus=PAID, chưa trừ refunds; không phải lợi nhuận") BigDecimal lifetimeValue,long appointmentCount) {}
    @Schema(name = "CrmCareNoteCommand", description = "Ghi chú nội bộ ADMIN/CS_STAFF; followUpAt chỉ lưu dữ liệu, không tự gửi email")
    public record NoteCommand(@Schema(description = "Thiếu/null dùng GENERAL, không trắng", maxLength = 30, example = "GENERAL") String noteType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 10000, example = "Nội dung trao đổi chăm sóc với khách") String content,
            @Schema(maxLength = 1000) String skinProfile, @Schema(maxLength = 1000) String allergies,
            @Schema(maxLength = 1000) String contraindications,
            @Schema(description = "Instant follow-up lưu trong CRM; không tự tạo Spa follow-up instruction", example = "2026-10-06T02:00:00Z") Instant followUpAt) {}
    @Schema(name = "CrmCareNoteView", description = "Ghi chú nội bộ chưa xóa; không tự cho phép khách đọc dữ liệu này")
    public record NoteView(Long id,Long userId,String noteType,String content,String skinProfile,String allergies,String contraindications,Instant followUpAt,Instant createdAt,String createdBy) {}
}

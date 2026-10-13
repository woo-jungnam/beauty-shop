package com.core.beautyshop.modules.crm.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface CustomerCareNoteRepository extends JpaRepository<CustomerCareNote, Long> {
    List<CustomerCareNote> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);
    Page<CustomerCareNote> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);
}

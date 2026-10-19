package com.core.beautyshop.modules.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BatchRecallRepository extends JpaRepository<BatchRecall,Long>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select r from BatchRecall r where r.id=:id") Optional<BatchRecall> lock(Long id);
 Optional<BatchRecall> findByReference(String reference);
 List<BatchRecall> findByVariantIdAndBatchCodeAndActiveTrue(Long variantId,String batchCode);
}

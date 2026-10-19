package com.core.beautyshop.modules.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RecallOutreachRepository extends JpaRepository<RecallOutreach,Long>{List<RecallOutreach> findByRecallId(Long id);Optional<RecallOutreach> findByRecallIdAndSubjectTypeAndSubjectId(Long recallId,String type,Long subjectId);}

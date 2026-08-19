package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {
    List<ProductAttributeValue> findByAttributeDefinitionId(Long definitionId);
    Optional<ProductAttributeValue> findByAttributeDefinitionIdAndValueString(Long definitionId, String valueString);
}

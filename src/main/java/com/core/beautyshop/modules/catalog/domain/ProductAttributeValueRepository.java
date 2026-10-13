package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {
    @EntityGraph(attributePaths = {"attributeDefinition"})
    List<ProductAttributeValue> findByAttributeDefinitionId(Long definitionId);
    boolean existsByAttributeDefinitionIdAndIsDeletedFalse(Long definitionId);
    Optional<ProductAttributeValue> findByAttributeDefinitionIdAndValueString(Long definitionId, String valueString);
}

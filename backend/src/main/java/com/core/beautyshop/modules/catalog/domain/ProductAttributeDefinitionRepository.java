package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProductAttributeDefinitionRepository extends JpaRepository<ProductAttributeDefinition, Long> {
    Optional<ProductAttributeDefinition> findByAttributeName(String attributeName);
    Optional<ProductAttributeDefinition> findByAttributeCode(String attributeCode);
}

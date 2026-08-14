package com.core.beautyshop.repository;

import com.core.beautyshop.entities.product.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {
    List<ProductAttributeValue> findByAttributeDefinitionId(Long definitionId);
    Optional<ProductAttributeValue> findByAttributeDefinitionIdAndValueString(Long definitionId, String valueString);
}
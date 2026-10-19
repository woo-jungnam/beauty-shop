package com.core.beautyshop.modules.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface InventoryConsumptionRepository extends JpaRepository<InventoryConsumption,Long> { Optional<InventoryConsumption> findByReference(String reference); }

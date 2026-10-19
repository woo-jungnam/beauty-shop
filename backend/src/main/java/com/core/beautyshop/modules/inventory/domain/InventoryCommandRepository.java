package com.core.beautyshop.modules.inventory.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface InventoryCommandRepository extends JpaRepository<InventoryCommand,Long>{Optional<InventoryCommand> findByCommandKey(String key);}

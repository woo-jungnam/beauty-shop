package com.core.beautyshop.modules.dashboard.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense,Long>{Optional<OperatingExpense> findByReference(String ref);}

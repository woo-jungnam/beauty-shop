package com.core.beautyshop.modules.dashboard.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense,Long>, JpaSpecificationExecutor<OperatingExpense>{Optional<OperatingExpense> findByReference(String ref);}

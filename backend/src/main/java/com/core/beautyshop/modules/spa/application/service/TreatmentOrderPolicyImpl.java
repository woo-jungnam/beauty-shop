package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.api.TreatmentOrderPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class TreatmentOrderPolicyImpl implements TreatmentOrderPolicy {
    @PersistenceContext private EntityManager em;
    @Override public boolean requiresTreatmentWorkflow(Long orderId) {
        if (orderId == null) return false;
        if (em.createQuery("select count(c) from TreatmentCourse c where c.orderId=:oid", Long.class).setParameter("oid", orderId).getSingleResult() > 0) return true;
        return em.createQuery("select count(c) from TreatmentChange c where c.settlementOrderId=:oid", Long.class).setParameter("oid", orderId).getSingleResult() > 0;
    }
}

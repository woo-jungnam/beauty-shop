package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketUsageState;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs without Spring, database containers or external processes. */
@ExtendWith(MockitoExtension.class)
class TreatmentFinancialConsumptionTest {
    @Mock EntityManager em;
    @Mock TypedQuery<FinancialConsumption> query;
    @InjectMocks TreatmentCourseService treatments;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(treatments, "em", em);
        when(em.createQuery(anyString(), eq(FinancialConsumption.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    Course course() { Course c = new Course(); c.setId(10L); return c; }
    SessionItem item() { SessionItem i = new SessionItem(); i.setId(20L); i.setAllocatedAmount(BigDecimal.valueOf(500)); return i; }
    AppointmentItem appointment(AppointmentItemExecutionStatus status, TicketUsageState usage) {
        AppointmentItem a = new AppointmentItem(); a.setId(30L); a.setExecutionStatus(status); a.setTicketUsageState(usage); return a;
    }

    @Test void forfeitedNoShowRecordsConsumedMoneyWithoutFabricatingClinicalPerformance() {
        when(query.getResultList()).thenReturn(List.of()); Course c = course();
        var appointment = appointment(AppointmentItemExecutionStatus.NO_SHOW, TicketUsageState.FORFEITED);
        treatments.recordConsumption(c, item(), appointment);
        assertEquals(0, BigDecimal.valueOf(500).compareTo(c.getConsumedAmount()));
        ArgumentCaptor<FinancialConsumption> row = ArgumentCaptor.forClass(FinancialConsumption.class); verify(em).persist(row.capture());
        assertEquals("FORFEIT", row.getValue().getKind()); assertEquals(20L, row.getValue().getSessionItemId());
        assertEquals(AppointmentItemExecutionStatus.NO_SHOW, appointment.getExecutionStatus());
    }

    @Test void repeatedPerformanceOrCompensatedRetryCannotConsumeTheSameTreatmentValueTwice() {
        FinancialConsumption prior = new FinancialConsumption();
        when(query.getResultList()).thenReturn(List.of(), List.of(prior)); Course c = course(); SessionItem item = item();
        treatments.recordConsumption(c, item, appointment(AppointmentItemExecutionStatus.PERFORMED, TicketUsageState.REDEEMED));
        treatments.recordConsumption(c, item, appointment(AppointmentItemExecutionStatus.PERFORMED, TicketUsageState.REDEEMED));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(c.getConsumedAmount())); verify(em, times(1)).persist(any(FinancialConsumption.class));
    }

    @Test void releasedNoShowAndSkippedItemsKeepTheirValueRefundable() {
        when(query.getResultList()).thenReturn(List.of()); Course c = course();
        treatments.recordConsumption(c, item(), appointment(AppointmentItemExecutionStatus.NO_SHOW, TicketUsageState.RELEASED));
        treatments.recordConsumption(c, item(), appointment(AppointmentItemExecutionStatus.SKIPPED, TicketUsageState.RELEASED));
        assertEquals(0, c.getConsumedAmount().signum()); verify(em, never()).persist(any());
    }
}

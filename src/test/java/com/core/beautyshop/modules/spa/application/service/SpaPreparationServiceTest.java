package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.crm.api.CustomerCareFacade;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import com.core.beautyshop.shared.outbox.application.service.OutboxService;
import com.core.beautyshop.shared.event.SpaNotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.TimeZone;

import static com.core.beautyshop.modules.spa.application.service.SpaPreparationService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual preparation SQL and V33 run against H2; only existing domain boundaries are mocked. */
class SpaPreparationServiceTest {
    private JdbcTemplate jdbc;
    private Appointment appointment;
    private AppointmentItem item;
    private CustomerCareFacade care;
    private SpaPreparationService preparation;
    private SpaAccessService access;
    private SpaNotificationWorkflow notifications;
    private OutboxService outbox;
    private TransactionTemplate transactions;

    @BeforeEach
    void setup() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:prep_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE roles(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(50),description VARCHAR(255),created_at TIMESTAMP,updated_at TIMESTAMP,is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE beauty_services(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE appointment_items(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE appointments(id BIGINT PRIMARY KEY,appointment_date DATE,start_time TIME,status VARCHAR(30),is_deleted BOOLEAN DEFAULT false,checked_in_at TIMESTAMP)");
        jdbc.update("INSERT INTO users VALUES (10),(20),(30),(40)");
        jdbc.update("INSERT INTO beauty_services VALUES (100)");
        jdbc.update("INSERT INTO appointment_items VALUES (200),(201)");
        jdbc.update("INSERT INTO appointments(id) VALUES (300)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__spa_versioned_preparation_and_roles.sql")).execute(source);
        BeautyService service = new BeautyService(); service.setId(100L);
        Staff therapist = new Staff(); therapist.setId(5L); therapist.setUserId(20L);
        appointment = Appointment.builder().userId(10L).status(AppointmentStatus.CONFIRMED).build(); appointment.setId(300L);
        appointment.setAppointmentDate(LocalDate.of(2026, 10, 3)); appointment.setStartTime(LocalTime.of(10, 0));
        item = AppointmentItem.builder().appointment(appointment).service(service).staff(therapist).build(); item.setId(200L);
        appointment.setItems(List.of(item));
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        when(appointments.findByIdWithItems(300L)).thenReturn(Optional.of(appointment));
        when(appointments.findByIdForUpdate(300L)).thenReturn(Optional.of(appointment));
        BeautyServiceRepository services = mock(BeautyServiceRepository.class);
        when(services.findByIdForUpdate(100L)).thenReturn(Optional.of(service));
        care = mock(CustomerCareFacade.class);
        when(care.getCareNotes(10L)).thenReturn(List.of(new CustomerCareFacade.CareNote(1L, "ALLERGY", "Customer reports fragrance sensitivity",
                null, "Fragrance", null, null, Instant.parse("2026-10-01T00:00:00Z"))));
        access = new SpaAccessService();
        preparation = new SpaPreparationService(jdbc, new ObjectMapper().findAndRegisterModules(), appointments, services, access, care);
        outbox = mock(OutboxService.class);
        notifications = new SpaNotificationWorkflow(jdbc, appointments, access, outbox);
        login(30L, "ADMIN");
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void ownerAndAssignedTherapistScopeIsBasedOnStaffUserIdAndDeletedAssignmentsAreIgnored() {
        login(10L, "CUSTOMER");
        assertThatCode(() -> access.requireCanViewAppointment(appointment)).doesNotThrowAnyException();
        assertThatThrownBy(() -> preparation.careSummary(300L)).isInstanceOf(AccessDeniedException.class);
        login(20L, "SPA_THERAPIST");
        assertThatCode(() -> access.requireCanPerformItem(item)).doesNotThrowAnyException();
        assertThat(preparation.careSummary(300L).notes()).hasSize(1);
        login(40L, "SPA_THERAPIST");
        assertThatThrownBy(() -> preparation.preparation(300L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> preparation.careSummary(300L)).isInstanceOf(AccessDeniedException.class);
        login(20L, "SPA_THERAPIST"); item.setIsDeleted(true);
        assertThat(access.isAssignedTechnician(appointment)).isFalse();
        assertThatThrownBy(() -> access.requireCanPerformItem(item)).isInstanceOf(AccessDeniedException.class);
        login(40L, "SPA_RECEPTION");
        assertThat(access.canManageReception()).isTrue();
        assertThatThrownBy(() -> access.requireCanPerformItem(item)).isInstanceOf(AccessDeniedException.class);
        login(40L, "CS_STAFF");
        assertThat(preparation.careSummary(300L).notes()).hasSize(1);
    }

    @Test
    void editingTemplateAndServicePolicyNeverChangesRequirementsForAnExistingBooking() {
        FormVersion first = preparation.createTemplate(form("Initial intake", "symptoms"));
        preparation.configureService(100L, new ServicePolicyCommand(false, List.of(first.id())));
        preparation.snapshotRequirements(appointment);
        FormVersion second = preparation.addVersion(first.templateId(), form("Updated intake", "history"));
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of(second.id())));
        preparation.snapshotRequirements(appointment); // Retrying booking hook must be idempotent.
        List<FormVersion> history = preparation.versions(first.templateId());
        assertThat(history).extracting(FormVersion::version).containsExactly(2, 1);
        assertThat(history.get(1).questions().get(0).key()).isEqualTo("symptoms");
        login(10L, "CUSTOMER");
        ItemPreparation frozen = preparation.preparation(300L).get(0);
        assertThat(frozen.warningsRequired()).isFalse();
        assertThat(frozen.forms()).hasSize(1);
        assertThat(frozen.forms().get(0).form().id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_preparation_snapshots", Integer.class)).isEqualTo(1);
    }

    @Test
    void customerMustSupplyBookedVersionAnswersAndConsentCannotBeForgedByStaffOrAnotherCustomer() {
        FormVersion form = preparation.createTemplate(form("Intake", "history"));
        preparation.configureService(100L, new ServicePolicyCommand(false, List.of(form.id())));
        preparation.snapshotRequirements(appointment);
        Long requirement = preparation.preparation(300L).get(0).forms().get(0).id();
        login(20L, "SPA_THERAPIST");
        assertThatThrownBy(() -> preparation.requireReadyForStart(item)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "None reported"))))
                .isInstanceOf(AccessDeniedException.class);
        login(30L, "ADMIN");
        assertThatThrownBy(() -> preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "None"))))
                .isInstanceOf(AccessDeniedException.class);
        login(40L, "CUSTOMER");
        assertThatThrownBy(() -> preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "None"))))
                .isInstanceOf(AccessDeniedException.class);
        login(10L, "CUSTOMER");
        assertThatThrownBy(() -> preparation.submit(300L, requirement, new ResponseCommand(false, Map.of("history", "None"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("wrongVersion", "None"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preparation.submit(300L, 9999L, new ResponseCommand(true, Map.of("history", "None"))))
                .isInstanceOf(ResourceNotFoundException.class);
        FormResponse response = preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "No symptoms reported")));
        assertThat(response.customerId()).isEqualTo(10L); assertThat(response.actorId()).isEqualTo(10L);
        login(20L, "SPA_THERAPIST");
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_form_responses", Integer.class)).isEqualTo(1);
    }

    @Test
    void warningAcknowledgmentRequiresCurrentHashAndActualStartingActor() {
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of()));
        preparation.snapshotRequirements(appointment);
        login(20L, "SPA_THERAPIST");
        String hash = preparation.careSummary(300L).versionHash();
        assertThatThrownBy(() -> preparation.requireReadyForStart(item)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preparation.acknowledgeWarnings(300L, 200L, new WarningCommand("stale")))
                .isInstanceOf(BusinessException.class);
        WarningAcknowledgment acknowledgment = preparation.acknowledgeWarnings(300L, 200L, new WarningCommand(hash));
        assertThat(acknowledgment.actorId()).isEqualTo(20L); assertThat(acknowledgment.acknowledgedAt()).isNotNull();
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        login(30L, "ADMIN");
        assertThatThrownBy(() -> preparation.requireReadyForStart(item)).isInstanceOf(BusinessException.class);
        login(20L, "SPA_THERAPIST");
        when(care.getCareNotes(10L)).thenReturn(List.of());
        assertThatThrownBy(() -> preparation.requireReadyForStart(item)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_warning_acknowledgments", Integer.class)).isEqualTo(1);
    }

    @Test
    void customerFormEditsInvalidateCareReviewWhileNewTemplateVersionsLeaveBookedFormsUnchanged() {
        FormVersion original = preparation.createTemplate(form("Intake", "history"));
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of(original.id())));
        preparation.snapshotRequirements(appointment);
        Long requirement = preparation.preparation(300L).get(0).forms().get(0).id();
        login(10L, "CUSTOMER");
        preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "None reported")));
        login(20L, "SPA_THERAPIST");
        String initialHash = preparation.careSummary(300L).versionHash();
        preparation.acknowledgeWarnings(300L, 200L, new WarningCommand(initialHash));
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        login(30L, "ADMIN");
        FormVersion newVersion = preparation.addVersion(original.templateId(), form("Updated intake", "newQuestion"));
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of(newVersion.id())));
        login(20L, "SPA_THERAPIST");
        assertThat(preparation.careSummary(300L).versionHash()).isEqualTo(initialHash);
        login(10L, "CUSTOMER");
        preparation.submit(300L, requirement, new ResponseCommand(true, Map.of("history", "New customer supplied information")));
        login(20L, "SPA_THERAPIST");
        CareSummary revised = preparation.careSummary(300L);
        assertThat(revised.versionHash()).isNotEqualTo(initialHash);
        assertThat(revised.forms().get(0).forms().get(0).form().id()).isEqualTo(original.id());
        assertThatThrownBy(() -> preparation.requireReadyForStart(item)).isInstanceOf(BusinessException.class);
        preparation.acknowledgeWarnings(300L, 200L, new WarningCommand(revised.versionHash()));
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_form_responses", Integer.class)).isEqualTo(2);
    }

    @Test
    void managerOverrideRequiresReasonAndStoresItsOwnEvidenceWithoutInventingCustomerConsent() {
        FormVersion form = preparation.createTemplate(form("Intake", "history"));
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of(form.id())));
        preparation.snapshotRequirements(appointment);
        login(40L, "SPA_RECEPTION");
        assertThatThrownBy(() -> preparation.override(300L, 200L, new OverrideCommand("Reason"))).isInstanceOf(AccessDeniedException.class);
        login(30L, "ADMIN");
        assertThatThrownBy(() -> preparation.override(300L, 200L, new OverrideCommand("  "))).isInstanceOf(BusinessException.class);
        OverrideEvidence evidence = preparation.override(300L, 200L, new OverrideCommand(" Reviewed with customer; exceptional manual approval "));
        assertThat(evidence.actorId()).isEqualTo(30L); assertThat(evidence.reason()).startsWith("Reviewed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_form_responses", Integer.class)).isZero();
        assertThat(preparation.preparation(300L).get(0).overrides()).singleElement()
                .satisfies(stored -> { assertThat(stored.actorId()).isEqualTo(evidence.actorId());
                    assertThat(stored.reason()).isEqualTo(evidence.reason()); assertThat(stored.createdAt()).isNotNull(); });
        login(20L, "SPA_THERAPIST");
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
    }

    @Test
    void legacyAndDefaultServicesRemainUnblockedAndLaterItemsCanPrepareDuringAppointmentProgress() {
        login(20L, "SPA_THERAPIST");
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        assertThat(preparation.preparation(300L).get(0).legacyBooking()).isTrue();
        preparation.snapshotRequirements(appointment);
        assertThatCode(() -> preparation.requireReadyForStart(item)).doesNotThrowAnyException();
        login(30L, "ADMIN");
        preparation.configureService(100L, new ServicePolicyCommand(true, List.of()));
        AppointmentItem later = AppointmentItem.builder().appointment(appointment).service(item.getService()).staff(item.getStaff()).build();
        later.setId(201L); appointment.setItems(List.of(item, later));
        preparation.snapshotRequirements(appointment);
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        item.setExecutionStatus(AppointmentItemExecutionStatus.IN_PROGRESS); item.setActualStartedAt(Instant.now());
        login(20L, "SPA_THERAPIST");
        String hash = preparation.careSummary(300L).versionHash();
        assertThatCode(() -> preparation.acknowledgeWarnings(300L, 201L, new WarningCommand(hash))).doesNotThrowAnyException();
        assertThatThrownBy(() -> preparation.acknowledgeWarnings(300L, 200L, new WarningCommand(hash))).isInstanceOf(BusinessException.class);
        assertThatCode(() -> preparation.requireReadyForStart(later)).doesNotThrowAnyException();
    }

    @Test
    void remindersDeduplicateEachScheduleRevisionAndDropCancelledRescheduledOrCheckedInDeliveries() {
        Instant now = Instant.parse("2026-10-03T02:00:00Z");
        assertThat(notifications.enqueueReminder(300L, now, 24)).isTrue();
        assertThat(notifications.enqueueReminder(300L, now, 24)).isFalse();
        SpaNotificationMessage original = new SpaNotificationMessage(300L, SpaNotificationMessage.Kind.REMINDER,
                appointment.getAppointmentDate(), appointment.getStartTime(), null);
        assertThat(notifications.currentDelivery(original, now)).isPresent();
        appointment.setStartTime(LocalTime.of(11, 0));
        assertThat(notifications.currentDelivery(original, now)).isEmpty();
        assertThat(notifications.enqueueReminder(300L, now, 24)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_notification_dispatches", Integer.class)).isEqualTo(2);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        assertThat(notifications.currentDelivery(original, now)).isEmpty();
        assertThat(notifications.enqueueReminder(300L, now, 24)).isFalse();
        appointment.setStatus(AppointmentStatus.CONFIRMED); appointment.setCheckedInAt(now);
        assertThat(notifications.enqueueReminder(300L, now, 24)).isFalse();
        verify(outbox, times(2)).recordEvent(eq("APPOINTMENT"), eq("300"), eq(SpaNotificationWorkflow.TOPIC), eq("300"), any());
        verifyNoInteractions(care);
    }

    @Test
    void followUpUsesOnlyAssignedStaffWrittenInstructionsAndQueuesOnceAfterAppointmentCompletion() {
        login(10L, "CUSTOMER"); item.setExecutionStatus(AppointmentItemExecutionStatus.PERFORMED);
        var command = new SpaNotificationWorkflow.InstructionCommand("Please follow the instructions provided during your visit.", Instant.now().plusSeconds(30));
        assertThatThrownBy(() -> notifications.addInstruction(300L, 200L, command)).isInstanceOf(AccessDeniedException.class);
        login(20L, "SPA_THERAPIST");
        var instruction = notifications.addInstruction(300L, 200L, command);
        assertThat(instruction.authorId()).isEqualTo(20L);
        assertThat(notifications.enqueueFollowUp(instruction.id(), instruction.dueAt().plusSeconds(1))).isFalse();
        appointment.setStatus(AppointmentStatus.COMPLETED);
        assertThat(notifications.enqueueFollowUp(instruction.id(), instruction.dueAt().minusSeconds(1))).isFalse();
        assertThat(notifications.enqueueFollowUp(instruction.id(), instruction.dueAt().plusSeconds(1))).isTrue();
        assertThat(notifications.enqueueFollowUp(instruction.id(), instruction.dueAt().plusSeconds(2))).isFalse();
        var message = new SpaNotificationMessage(300L, SpaNotificationMessage.Kind.FOLLOW_UP, appointment.getAppointmentDate(),
                appointment.getStartTime(), instruction.id());
        assertThat(notifications.currentDelivery(message, instruction.dueAt().plusSeconds(1))).get()
                .satisfies(delivery -> assertThat(delivery.body()).isEqualTo(command.content()));
        login(10L, "CUSTOMER");
        assertThat(notifications.instructions(300L)).hasSize(1);
        login(40L, "CUSTOMER");
        assertThatThrownBy(() -> notifications.instructions(300L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(care);
    }

    @Test
    void outboxFailureRollsBackProducerDedupSoAValidRetryCanStillQueueTheReminder() {
        Instant now = Instant.parse("2026-10-03T02:00:00Z");
        doThrow(new IllegalStateException("Outbox unavailable")).when(outbox).recordEvent(anyString(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> notifications.enqueueReminder(300L, now, 24)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_notification_dispatches", Integer.class)).isZero();
        reset(outbox);
        Boolean queued = transactions.execute(status -> notifications.enqueueReminder(300L, now, 24));
        assertThat(queued).isTrue();
    }

    @Test
    void remindersHandleOrmUtcTimeBindingAroundMidnightAndTimestampsStayUtcOnNonUtcJvm() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Instant now = Instant.parse("2026-10-02T19:00:00Z"); // 02:00 local; appointment today at 10:00 is still future.
            SpaJdbcSupport.update(jdbc, "UPDATE appointments SET appointment_date=?,start_time=?,status='CONFIRMED' WHERE id=?",
                    appointment.getAppointmentDate(), appointment.getStartTime(), 300L);
            SpaNotificationScheduler scheduler = new SpaNotificationScheduler(jdbc, notifications);
            ReflectionTestUtils.setField(scheduler, "jobsEnabled", true);
            ReflectionTestUtils.setField(scheduler, "hoursAhead", 24);
            scheduler.enqueueDueNotifications(now);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_notification_dispatches", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spa_notification_dispatches d JOIN appointments a ON a.id=d.appointment_id WHERE d.start_time=a.start_time", Integer.class)).isEqualTo(1);
            var stored = jdbc.query("SELECT start_time FROM appointments", (rs, row) -> SpaJdbcSupport.time(rs, "start_time"));
            assertThat(stored).containsExactly(LocalTime.of(10, 0));
            var instants = jdbc.query("SELECT enqueued_at FROM spa_notification_dispatches", (rs, row) -> SpaJdbcSupport.instant(rs, "enqueued_at"));
            assertThat(instants).containsExactly(now);
            var rawUtc = jdbc.queryForObject("SELECT enqueued_at FROM spa_notification_dispatches", java.time.LocalDateTime.class);
            assertThat(rawUtc).isEqualTo(java.time.LocalDateTime.of(2026, 10, 2, 19, 0));
            scheduler.enqueueDueNotifications(now);
            verify(outbox, times(1)).recordEvent(eq("APPOINTMENT"), eq("300"), eq(SpaNotificationWorkflow.TOPIC), eq("300"), any());
        } finally { TimeZone.setDefault(previous); }
    }

    private FormCommand form(String title, String key) {
        return new FormCommand(title, List.of(new FormQuestion(key, "Please describe symptoms or history in your own words", QuestionType.TEXT, true)));
    }

    private void login(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new UserDetailsImpl(id, "user" + id, "user" + id + "@example.test", "unused", authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}

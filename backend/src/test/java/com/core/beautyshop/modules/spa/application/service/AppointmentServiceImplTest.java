package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BeautyServiceRepository beautyServiceRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private UserServiceTicketRepository ticketRepository;

    @Mock
    private IdentityFacade identityFacade;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private static final Long USER_ID = 50L;

    @BeforeEach
    void setUpSecurity() {
        UserDetailsImpl userDetails = new UserDetailsImpl(
                USER_ID,
                "customer1",
                "customer1@beautyshop.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBookAppointment_WithTicket_DeductsSessionAndSetsPriceZero() {
        BeautyService service = BeautyService.builder()
                .name("Chăm sóc da mặt cơ bản")
                .basePrice(new BigDecimal("300000"))
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        service.setId(1L);

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);

        AppointmentItemRequest itemRequest = new AppointmentItemRequest();
        itemRequest.setServiceId(1L);
        itemRequest.setTicketId(10L);

        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setNotes("Hen cuoi tuan");
        request.setItems(List.of(itemRequest));

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(USER_ID)
                .fullName("Khách Hàng A")
                .build();

        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(userSummary);
        when(beautyServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment apt = inv.getArgument(0);
            apt.setId(100L);
            return apt;
        });

        AppointmentResponse response = appointmentService.bookAppointment(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Khách Hàng A", response.getCustomerName());
        assertEquals(1, response.getItems().size());
        assertEquals(BigDecimal.ZERO, response.getItems().get(0).getPrice());
        assertEquals(10L, response.getItems().get(0).getTicketId());
        assertTrue(response.getItems().get(0).getIsTicketUsed());

        // Kiểm tra số buổi đã dùng được tăng lên 1
        assertEquals(1, ticket.getUsedSessions());
        verify(ticketRepository).save(ticket);
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void testCancelAppointment_RestoresTicketSession() {
        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .totalSessions(5)
                .usedSessions(2)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);

        AppointmentItem item = AppointmentItem.builder()
                .ticket(ticket)
                .price(BigDecimal.ZERO)
                .build();

        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>(List.of(item)))
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdWithItems(100L)).thenReturn(Optional.of(appointment));

        appointmentService.cancelAppointment(100L);

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        // Số buổi đã dùng được giảm từ 2 về 1
        assertEquals(1, ticket.getUsedSessions());
        verify(ticketRepository).save(ticket);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void testCancelAppointment_PastAppointment_ThrowsException() {
        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().minusDays(1)) // Lịch hẹn hôm qua
                .startTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdWithItems(100L)).thenReturn(Optional.of(appointment));

        assertThrows(BusinessException.class, () -> appointmentService.cancelAppointment(100L));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testBookAppointment_ServiceNotInTicketPackage_ThrowsException() {
        BeautyService serviceA = BeautyService.builder()
                .name("Chăm sóc da mặt")
                .basePrice(new BigDecimal("300000"))
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        serviceA.setId(1L);

        BeautyService serviceB = BeautyService.builder()
                .name("Massage Body")
                .basePrice(new BigDecimal("500000"))
                .durationMinutes(60)
                .preparationTimeMinutes(15)
                .build();
        serviceB.setId(2L);

        // Gói chỉ chứa Service B
        ServicePackage pkg = ServicePackage.builder()
                .name("Gói Massage Body")
                .items(List.of(ServicePackageItem.builder().service(serviceB).quantity(5).build()))
                .build();

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);

        AppointmentItemRequest itemRequest = new AppointmentItemRequest();
        itemRequest.setServiceId(1L); // Đặt Service A nhưng dùng vé của Gói chỉ có Service B
        itemRequest.setTicketId(10L);

        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setItems(List.of(itemRequest));

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(USER_ID)
                .fullName("Khách Hàng A")
                .build();

        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(userSummary);
        when(beautyServiceRepository.findById(1L)).thenReturn(Optional.of(serviceA));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(BusinessException.class, () -> appointmentService.bookAppointment(request));
        verify(appointmentRepository, never()).save(any());
    }
}

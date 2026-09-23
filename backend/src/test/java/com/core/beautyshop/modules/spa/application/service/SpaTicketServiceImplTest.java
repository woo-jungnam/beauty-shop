package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.impl.SpaTicketServiceImpl;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpaTicketServiceImplTest {

    @Mock
    private UserServiceTicketRepository ticketRepository;

    @InjectMocks
    private SpaTicketServiceImpl spaTicketService;

    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUpSecurity() {
        UserDetailsImpl userDetails = new UserDetailsImpl(
                USER_ID,
                "testcustomer",
                "customer@beautyshop.com",
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
    void testGetMyActiveTickets_FiltersExpiredAndFullUsed() {
        ServicePackage pkg = ServicePackage.builder().name("Gói Spa 1").build();
        pkg.setId(1L);

        UserServiceTicket activeTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .orderId(1000L)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(2)
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        activeTicket.setId(10L);

        UserServiceTicket expiredTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .orderId(1001L)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(1)
                .expiryDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        expiredTicket.setId(11L);

        UserServiceTicket fullUsedTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .orderId(1002L)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(5)
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        fullUsedTicket.setId(12L);

        UserServiceTicket legacyTicketWithoutOrder = UserServiceTicket.builder()
                .userId(USER_ID)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        legacyTicketWithoutOrder.setId(13L);

        when(ticketRepository.findByUserIdAndStatusOrderByCreatedAtDesc(USER_ID, TicketStatus.ACTIVE))
                .thenReturn(List.of(activeTicket, expiredTicket, fullUsedTicket, legacyTicketWithoutOrder));

        List<UserServiceTicketResponse> activeTickets = spaTicketService.getMyActiveTickets();

        assertEquals(1, activeTickets.size());
        assertEquals(10L, activeTickets.get(0).getId());
        assertEquals(3, activeTickets.get(0).getRemainingSessions());
    }
}

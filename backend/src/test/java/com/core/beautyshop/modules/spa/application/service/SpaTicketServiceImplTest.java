package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.impl.SpaTicketServiceImpl;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageItem;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpaTicketServiceImplTest {

    @Mock
    private UserServiceTicketRepository ticketRepository;

    @Mock
    private ServicePackageRepository packageRepository;

    @Mock
    private IdentityFacade identityFacade;

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
    void testPurchasePackage_Success() {
        PurchasePackageRequest request = new PurchasePackageRequest(1L, "Mua goi cham soc da");

        ServicePackage servicePackage = ServicePackage.builder()
                .name("Gói Trị Mụn Chuyên Sâu")
                .price(new BigDecimal("1500000"))
                .validityDays(90)
                .isActive(true)
                .items(List.of(
                        ServicePackageItem.builder().quantity(5).build(),
                        ServicePackageItem.builder().quantity(5).build()
                ))
                .build();
        servicePackage.setId(1L);

        when(identityFacade.existsById(USER_ID)).thenReturn(true);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(servicePackage));
        when(ticketRepository.save(any(UserServiceTicket.class))).thenAnswer(invocation -> {
            UserServiceTicket ticket = invocation.getArgument(0);
            ticket.setId(200L);
            return ticket;
        });

        UserServiceTicketResponse response = spaTicketService.purchasePackage(request);

        assertNotNull(response);
        assertEquals(200L, response.getId());
        assertEquals(USER_ID, response.getUserId());
        assertEquals("Gói Trị Mụn Chuyên Sâu", response.getPackageName());
        assertEquals(10, response.getTotalSessions());
        assertEquals(0, response.getUsedSessions());
        assertEquals(10, response.getRemainingSessions());
        assertEquals(TicketStatus.ACTIVE, response.getStatus());
        assertNotNull(response.getExpiryDate());

        verify(ticketRepository).save(any(UserServiceTicket.class));
    }

    @Test
    void testPurchasePackage_InactivePackage_ThrowsException() {
        PurchasePackageRequest request = new PurchasePackageRequest(1L, "Mua goi");

        ServicePackage servicePackage = ServicePackage.builder()
                .name("Gói Đã Ngừng Phục Vụ")
                .price(new BigDecimal("1000000"))
                .isActive(false)
                .build();
        servicePackage.setId(1L);

        when(identityFacade.existsById(USER_ID)).thenReturn(true);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(servicePackage));

        assertThrows(BusinessException.class, () -> spaTicketService.purchasePackage(request));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void testPurchasePackage_PackageNotFound_ThrowsException() {
        PurchasePackageRequest request = new PurchasePackageRequest(999L, "Mua goi");

        when(identityFacade.existsById(USER_ID)).thenReturn(true);
        when(packageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> spaTicketService.purchasePackage(request));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void testGetMyActiveTickets_FiltersExpiredAndFullUsed() {
        ServicePackage pkg = ServicePackage.builder().name("Gói Spa 1").build();
        pkg.setId(1L);

        UserServiceTicket activeTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(2)
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        activeTicket.setId(10L);

        UserServiceTicket expiredTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(1)
                .expiryDate(Instant.now().minus(5, ChronoUnit.DAYS)) // Đã hết hạn
                .status(TicketStatus.ACTIVE)
                .build();
        expiredTicket.setId(11L);

        UserServiceTicket fullUsedTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(5) // Đã dùng hết
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        fullUsedTicket.setId(12L);

        when(ticketRepository.findByUserIdAndStatusOrderByCreatedAtDesc(USER_ID, TicketStatus.ACTIVE))
                .thenReturn(List.of(activeTicket, expiredTicket, fullUsedTicket));

        List<UserServiceTicketResponse> activeTickets = spaTicketService.getMyActiveTickets();

        assertEquals(1, activeTickets.size());
        assertEquals(10L, activeTickets.get(0).getId());
        assertEquals(3, activeTickets.get(0).getRemainingSessions());
    }
}

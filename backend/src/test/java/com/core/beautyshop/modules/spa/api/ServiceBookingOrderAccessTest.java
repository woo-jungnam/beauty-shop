package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.ServiceBookingOrderQuery;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceBookingOrderAccessTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean ServiceBookingOrderQuery query() { return mock(ServiceBookingOrderQuery.class); }
        @Bean ServiceBookingOrderController controller(ServiceBookingOrderQuery query) { return new ServiceBookingOrderController(query); }
    }

    AnnotationConfigApplicationContext context;
    ServiceBookingOrderController controller;
    ServiceBookingOrderQuery query;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(ServiceBookingOrderController.class);
        query = context.getBean(ServiceBookingOrderQuery.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    void login(String role) {
        var authorities = List.of(new SimpleGrantedAuthority(role));
        var principal = new UserDetailsImpl(7L, "test", "test@example.test", "unused", authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test void customerCanOnlyQueryTheirOwnBookingsAndCannotUseTheAdminList() {
        login("ROLE_CUSTOMER");
        when(query.list(eq(7L), any())).thenReturn(Page.empty());
        controller.mine(PageRequest.of(0, 10));
        verify(query).list(eq(7L), any());
        assertThrows(AccessDeniedException.class, () -> controller.admin(PageRequest.of(0, 10)));
        verify(query, never()).list(isNull(), any());
    }

    @Test void adminCanReadAllServiceBookings() {
        login("ROLE_ADMIN");
        when(query.list(isNull(), any())).thenReturn(Page.empty());
        controller.admin(PageRequest.of(0, 10));
        verify(query).list(isNull(), any());
    }
}

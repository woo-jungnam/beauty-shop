package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.FlashSaleService;
import com.core.beautyshop.modules.promotion.application.FlashSaleService.CampaignCommand;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminFlashSaleControllerAccessTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean FlashSaleService service() { return mock(FlashSaleService.class); }
        @Bean AdminFlashSaleController controller(FlashSaleService service) { return new AdminFlashSaleController(service); }
    }

    private AnnotationConfigApplicationContext context;
    private AdminFlashSaleController controller;
    private FlashSaleService service;
    private final CampaignCommand command = new CampaignCommand("Flash Sale", null, null,
            Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), null, List.of());

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(AdminFlashSaleController.class);
        service = context.getBean(FlashSaleService.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    void actor(String role) {
        var authorities = List.of(new SimpleGrantedAuthority(role));
        var principal = new UserDetailsImpl(7L, "test", "test@example.test", "unused", authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_ADMIN"})
    void staffAndAdminCanReadAtTheRoleLayer(String role) {
        actor(role);
        var pageable = PageRequest.of(0, 10);
        when(service.findAll(pageable)).thenReturn(Page.empty());
        controller.list(pageable);
        controller.getById(7L);
        controller.getCandidateItems();
        verify(service).findAll(pageable);
        verify(service).getById(7L);
        verify(service).getCandidateItems();
    }
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_CUSTOMER"})
    void nonAdminCannotCreateUpdateOrDelete(String role) {
        actor(role);
        assertThrows(AccessDeniedException.class, () -> controller.create(command));
        assertThrows(AccessDeniedException.class, () -> controller.update(7L, command));
        assertThrows(AccessDeniedException.class, () -> controller.delete(7L));
        verifyNoInteractions(service);
    }
    @Test void adminCanCreateUpdateAndDelete() {
        actor("ROLE_ADMIN");
        controller.create(command);
        controller.update(7L, command);
        controller.delete(7L);
        verify(service).create(command);
        verify(service).update(7L, command);
        verify(service).delete(7L);
    }
}

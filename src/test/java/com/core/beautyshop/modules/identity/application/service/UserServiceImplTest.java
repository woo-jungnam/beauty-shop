package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAward;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAwardRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.MembershipTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private LoyaltyPointAwardRepository loyaltyPointAwardRepository;
    @Mock private AuthMapper authMapper;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void addsPointsWhileHoldingUserLockAndRecordsOrderAward() {
        User user = User.builder()
                .username("customer")
                .fullName("Customer")
                .loyaltyPoints(990)
                .membershipTier(MembershipTier.MEMBER)
                .build();
        user.setId(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(loyaltyPointAwardRepository.existsByOrderId(100L)).thenReturn(false);

        userService.addLoyaltyPoints(1L, 100L, 20);

        assertEquals(1010, user.getLoyaltyPoints());
        assertEquals(MembershipTier.SILVER, user.getMembershipTier());
        verify(userRepository).save(user);
        ArgumentCaptor<LoyaltyPointAward> captor = ArgumentCaptor.forClass(LoyaltyPointAward.class);
        verify(loyaltyPointAwardRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getOrderId());
        assertEquals(20, captor.getValue().getPoints());
    }

    @Test
    void ignoresDuplicateAwardForSameOrder() {
        User user = User.builder().username("customer").fullName("Customer").loyaltyPoints(100).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(loyaltyPointAwardRepository.existsByOrderId(100L)).thenReturn(true);

        userService.addLoyaltyPoints(1L, 100L, 20);

        assertEquals(100, user.getLoyaltyPoints());
        verify(userRepository, never()).save(any());
        verify(loyaltyPointAwardRepository, never()).save(any());
    }
}

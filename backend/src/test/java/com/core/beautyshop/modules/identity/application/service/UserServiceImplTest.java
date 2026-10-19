package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAward;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAwardRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.MembershipTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void reversesLoyaltyPointsAndUpdatesTier() {
        User user = User.builder()
                .username("customer")
                .fullName("Customer")
                .loyaltyPoints(1010)
                .membershipTier(MembershipTier.SILVER)
                .build();
        user.setId(1L);

        LoyaltyPointAward award = LoyaltyPointAward.builder()
                .orderId(100L)
                .userId(1L)
                .points(20)
                .reversed(false)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(loyaltyPointAwardRepository.findByOrderId(100L)).thenReturn(Optional.of(award));

        userService.reverseLoyaltyPoints(1L, 100L);

        assertEquals(990, user.getLoyaltyPoints());
        assertEquals(MembershipTier.MEMBER, user.getMembershipTier());
        assertTrue(award.isReversed());
        verify(loyaltyPointAwardRepository).save(award);
        verify(userRepository).save(user);
    }

    @Test
    void ignoresAlreadyReversedLoyaltyAward() {
        User user = User.builder().username("customer").fullName("Customer").loyaltyPoints(100).build();
        user.setId(1L);

        LoyaltyPointAward award = LoyaltyPointAward.builder()
                .orderId(100L)
                .userId(1L)
                .points(20)
                .reversed(true)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(loyaltyPointAwardRepository.findByOrderId(100L)).thenReturn(Optional.of(award));

        userService.reverseLoyaltyPoints(1L, 100L);

        assertEquals(100, user.getLoyaltyPoints());
        verify(userRepository, never()).save(any());
    }
}

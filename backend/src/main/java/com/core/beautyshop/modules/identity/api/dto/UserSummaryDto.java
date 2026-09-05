package com.core.beautyshop.modules.identity.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private com.core.beautyshop.modules.identity.domain.enums.MembershipTier membershipTier;
    private Integer loyaltyPoints;
}

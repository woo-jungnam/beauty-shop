package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import java.util.Optional;

public interface IdentityFacade {
    Optional<UserSummaryDto> findUserSummaryById(Long userId);
    UserSummaryDto getUserSummaryById(Long userId);
    boolean existsById(Long userId);
}

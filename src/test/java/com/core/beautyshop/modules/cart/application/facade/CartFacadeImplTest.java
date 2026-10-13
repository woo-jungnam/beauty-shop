package com.core.beautyshop.modules.cart.application.facade;

import com.core.beautyshop.modules.cart.application.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartFacadeImplTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartFacadeImpl cartFacade;

    @Test
    void delegatesIdentifierBasedClearToService() {
        cartFacade.clearCartByUserIdOrSessionId(10L, "guest-session");

        verify(cartService).clearCartByUserIdOrSessionId(10L, "guest-session");
    }
}

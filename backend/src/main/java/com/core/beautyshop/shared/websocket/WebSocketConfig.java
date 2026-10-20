package com.core.beautyshop.shared.websocket;

import com.core.beautyshop.shared.config.CorsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PaymentWebSocketHandler paymentWebSocketHandler;
    private final PaymentSubscriptionHandshake paymentSubscriptionHandshake;
    private final CorsConfig corsConfig;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(paymentWebSocketHandler, "/ws/payment")
                .addInterceptors(paymentSubscriptionHandshake)
                .setAllowedOrigins(corsConfig.getAllowedOrigins().toArray(String[]::new));
    }
}

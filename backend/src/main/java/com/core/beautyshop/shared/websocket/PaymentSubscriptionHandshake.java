package com.core.beautyshop.shared.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

@Component @RequiredArgsConstructor
public class PaymentSubscriptionHandshake implements HandshakeInterceptor {
    private final PaymentSubscriptionTokenService tokens;
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String,Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("subscriptionToken");
        var scope = tokens.verify(token);
        if (scope.isEmpty()) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
        attributes.put("paymentScope", scope.get());
        return true;
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {}
}

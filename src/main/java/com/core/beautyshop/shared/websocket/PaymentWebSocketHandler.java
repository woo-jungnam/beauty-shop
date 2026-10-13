package com.core.beautyshop.shared.websocket;

import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // Map orderIdentifier (either orderCode or orderId as string) -> Set of active WebSocket sessions
    private final Map<String, Set<WebSocketSession>> orderSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionToOrderKeys = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return;

        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        String orderCode = queryParams.get("orderCode");
        String orderId = queryParams.get("orderId");

        Set<String> registeredKeys = new HashSet<>();
        if (orderCode != null && !orderCode.isBlank()) {
            registeredKeys.add(orderCode.trim());
        }
        if (orderId != null && !orderId.isBlank()) {
            registeredKeys.add(orderId.trim());
        }

        for (String key : registeredKeys) {
            orderSessions.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(session);
        }
        sessionToOrderKeys.put(session.getId(), registeredKeys);

        log.info("WebSocket connected: sessionId={}, watching orderKeys={}", session.getId(), registeredKeys);

        try {
            Map<String, Object> welcome = Map.of(
                    "type", "CONNECTED",
                    "sessionId", session.getId(),
                    "watching", registeredKeys
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
        } catch (IOException e) {
            log.warn("Could not send welcome message to WebSocket session {}", session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> keys = sessionToOrderKeys.remove(session.getId());
        if (keys != null) {
            for (String key : keys) {
                Set<WebSocketSession> sessions = orderSessions.get(key);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        orderSessions.remove(key);
                    }
                }
            }
        }
        log.info("WebSocket disconnected: sessionId={}, code={}", session.getId(), status.getCode());
    }

    @EventListener
    public void handleOrderPaidEvent(OrderEvents.OrderPaidEvent event) {
        if (event == null) return;
        log.info("Received OrderPaidEvent for orderId={}, orderNumber={}, broadcasting via WebSocket",
                event.getOrderId(), event.getOrderNumber());
        broadcastPaymentSuccess(event.getOrderNumber(), event.getOrderId(), event.getTotalAmount());
    }

    public void broadcastPaymentSuccess(String orderNumber, Long orderId, BigDecimal amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PAYMENT_SUCCESS");
        payload.put("status", "PAID");
        payload.put("orderNumber", orderNumber);
        payload.put("orderId", orderId);
        payload.put("amount", amount != null ? amount : BigDecimal.ZERO);
        payload.put("timestamp", System.currentTimeMillis());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Failed to serialize payment success message", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        Set<WebSocketSession> targetSessions = new HashSet<>();

        if (orderNumber != null) {
            Set<WebSocketSession> byCode = orderSessions.get(orderNumber);
            if (byCode != null) targetSessions.addAll(byCode);
        }
        if (orderId != null) {
            Set<WebSocketSession> byId = orderSessions.get(String.valueOf(orderId));
            if (byId != null) targetSessions.addAll(byId);
        }

        log.info("Broadcasting PAYMENT_SUCCESS to {} sessions for orderNumber={}, orderId={}",
                targetSessions.size(), orderNumber, orderId);

        for (WebSocketSession s : targetSessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to send payment message to session {}", s.getId(), e);
                }
            }
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            } else if (kv.length == 1) {
                map.put(kv[0].trim(), "");
            }
        }
        return map;
    }
}

package com.core.beautyshop;

import com.core.beautyshop.modules.catalog.infrastructure.RecommendationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationClientTest {
    HttpServer server;
    RecommendationClient client;
    AtomicReference<String> body = new AtomicReference<>("{\"status\":\"success\",\"recommended_product_ids\":[2,3]}");
    AtomicReference<String> token = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/recommend",exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Recommendation-Token"));
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        client=new RecommendationClient("http://127.0.0.1:"+server.getAddress().getPort(),new ObjectMapper(),1);
        ReflectionTestUtils.setField(client,"internalToken","fixture-internal-token");
    }
    @AfterEach void close() { server.stop(0); }

    @Test void authenticatedEngineRequestPreservesRanking() {
        var result=client.similar(1L,8);
        assertTrue(result.available());assertEquals(java.util.List.of(2L,3L),result.ids());
        assertEquals("fixture-internal-token",token.get());
    }
    @Test void confirmedEmptyIsDifferentFromUnavailable() {
        body.set("{\"status\":\"success\",\"recommended_product_ids\":[]}");
        assertTrue(client.forUser(null,null,8).available());
        body.set("{\"status\":\"unavailable\",\"recommended_product_ids\":[]}");
        assertFalse(client.forUser(null,null,8).available());
    }
    @Test void invalidNonIntegerIdsAreRejected() {
        body.set("{\"status\":\"success\",\"recommended_product_ids\":[2.5]}");
        assertFalse(client.similar(1L,8).available());
    }
    @Test void malformedResponseCannotPretendEngineSucceeded() {
        body.set("{\"status\":\"success\",\"ids\":[2]}");
        assertFalse(client.similar(1L,8).available());
    }
}

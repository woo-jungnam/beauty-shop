package com.core.beautyshop.modules.chatbot.api;

import com.core.beautyshop.modules.chatbot.application.dto.request.ChatRequest;
import com.core.beautyshop.modules.chatbot.application.dto.response.ChatResponse;
import com.core.beautyshop.modules.chatbot.application.dto.response.ProductCardResponse;
import com.core.beautyshop.modules.chatbot.application.service.ChatbotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class ChatbotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatbotService chatbotService;

    @InjectMocks
    private ChatbotController chatbotController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatbotController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/chat thành công trả về HTTP 200 cùng câu trả lời và sản phẩm đề xuất")
    void testChatEndpoint_Success() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .sessionId("sess_test_123")
                .message("Tư vấn kem dưỡng cho da khô")
                .build();

        ProductCardResponse product = ProductCardResponse.builder()
                .id("prod_01")
                .name("Kem dưỡng ẩm chuyên sâu")
                .brand("BeautyBrand")
                .price(350000.0)
                .build();

        ChatResponse response = ChatResponse.builder()
                .sessionId("sess_test_123")
                .intent("PRODUCT_RECOMMENDATION")
                .answer("Dưới đây là kem dưỡng phù hợp với làn da của bạn")
                .products(List.of(product))
                .build();

        when(chatbotService.chat(any(ChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/chatbot/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.session_id").value("sess_test_123"))
                .andExpect(jsonPath("$.data.answer").value("Dưới đây là kem dưỡng phù hợp với làn da của bạn"))
                .andExpect(jsonPath("$.data.products[0].name").value("Kem dưỡng ẩm chuyên sâu"));
    }

    @Test
    @DisplayName("GET /api/v1/chatbot/health trả về trạng thái ok")
    void testHealthEndpoint_Success() throws Exception {
        when(chatbotService.checkHealth()).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(get("/api/v1/chatbot/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/sync/database trả về trạng thái đồng bộ thành công")
    void testSyncDatabaseEndpoint_Success() throws Exception {
        when(chatbotService.syncDatabase(50)).thenReturn(Map.of("status", "ok", "synced", 50));

        mockMvc.perform(post("/api/v1/chatbot/sync/database").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.synced").value(50));
    }
}

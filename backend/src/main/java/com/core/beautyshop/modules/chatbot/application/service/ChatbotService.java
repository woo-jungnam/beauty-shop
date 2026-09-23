package com.core.beautyshop.modules.chatbot.application.service;

import com.core.beautyshop.modules.chatbot.application.dto.request.ChatRequest;
import com.core.beautyshop.modules.chatbot.application.dto.request.UnderstandTestRequest;
import com.core.beautyshop.modules.chatbot.application.dto.response.ChatResponse;

import java.io.OutputStream;
import java.util.Map;

public interface ChatbotService {

    ChatResponse chat(ChatRequest request);

    void streamChat(ChatRequest request, OutputStream outputStream);

    Map<String, Object> syncDatabase(Integer limit);

    Object testUnderstand(UnderstandTestRequest request);

    Map<String, Object> checkHealth();

    String getOpenApiJson();
}

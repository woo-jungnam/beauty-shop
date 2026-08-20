package com.core.beautyshop.modules.notification.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageDto implements Serializable {
    private String recipientEmail;
    private String recipientName;
    private String subject;
    private String templateCode;
    private Map<String, Object> parameters;
}

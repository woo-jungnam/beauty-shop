package com.core.beautyshop.modules.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInstruction {
    private String method;
    private String instructionMessage;
    private String bankName;
    private String bankAccountName;
    private String bankAccountNumber;
    private String transferSyntax;
    private String qrCodeUrl;
}

package com.core.beautyshop.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInstruction {
    private String method; // COD, BANK
    private String instructionMessage;
    private String bankName;
    private String bankAccountName;
    private String bankAccountNumber;
    private String transferSyntax;
    private String qrCodeUrl;
}

package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.dto.response.PaymentInstruction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service("bankPaymentStrategy")
public class BankPaymentStrategy implements PaymentStrategy {

    @Value("${sepay.bank.name:MBBank}")
    private String bankName;

    @Value("${sepay.bank.account-name:BEAUTY SHOP}")
    private String bankAccountName;

    @Value("${sepay.bank.account-number:123456789}")
    private String bankAccountNumber;

    @Value("${sepay.bank.bin:970422}")
    private String bankBin;

    @Override
    public PaymentInstruction processPayment(PaymentOrderDto order) {
        String transferSyntax = order.getOrderNumber();
        String amount = order.getTotalAmount() != null ? order.getTotalAmount().toBigInteger().toString() : "0";

        String encodedAccountName = URLEncoder.encode(bankAccountName, StandardCharsets.UTF_8);
        String encodedAddInfo = URLEncoder.encode(transferSyntax, StandardCharsets.UTF_8);
        String qrCodeUrl = String.format("https://img.vietqr.io/image/%s-%s-compact.jpg?amount=%s&addInfo=%s&accountName=%s",
                bankBin, bankAccountNumber, amount, encodedAddInfo, encodedAccountName);

        return PaymentInstruction.builder()
                .method("BANK")
                .instructionMessage("Vui lòng chuyển khoản số tiền " + order.getTotalAmount() + " VNĐ theo thông tin dưới đây. Đơn hàng sẽ tự động xác nhận sau khi nhận được thanh toán.")
                .bankName(bankName)
                .bankAccountName(bankAccountName)
                .bankAccountNumber(bankAccountNumber)
                .transferSyntax(transferSyntax)
                .qrCodeUrl(qrCodeUrl)
                .build();
    }
}

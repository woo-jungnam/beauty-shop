package com.core.beautyshop.modules.payment.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Payload SePay: cần referenceCode không trống (tối đa 100 ký tự), hoặc id dương. Không gửi Idempotency-Key; provider reference/id chống cộng tiền trùng")
public class SePayWebhookRequest {
    @Schema(description = "ID nhà cung cấp dương; dùng SEPAY-ID-{id} khi referenceCode trống", example = "123456")
    private Long id;
    
    @JsonProperty("gateway")
    @Schema(description = "Metadata ngân hàng nhà cung cấp; không quyết định gateway ledger (luôn SEPAY)", example = "Vietcombank")
    private String gateway;
    
    @JsonProperty("transactionDate")
    @Schema(description = "Ngày giao dịch dạng chuỗi của nhà cung cấp; lưu để đối soát, không dùng làm thời điểm báo cáo ledger", example = "2026-10-02 09:00:00")
    private String transactionDate;
    
    @JsonProperty("accountNumber")
    @Schema(description = "Tài khoản nhận; phải khớp tài khoản SePay cấu hình để ghi tiền vào order", example = "0123456789")
    private String accountNumber;
    
    @JsonProperty("subAccount")
    @Schema(description = "Tài khoản phụ theo nhà cung cấp, tùy chọn")
    private String subAccount;
    
    @JsonProperty("code")
    @Schema(description = "Metadata provider; mã order được đọc từ content, không từ trường này")
    private String code;
    
    @JsonProperty("content")
    @Schema(description = "Nội dung có mã ORD- gồm 8 hoặc 32 ký tự chữ/số, không phân biệt hoa thường", example = "Thanh toan ORD-1234ABCD")
    private String content;
    
    @JsonProperty("transferType")
    @Schema(description = "in xử lý tiền nhận (không phân biệt hoa thường); giá trị khác lưu IGNORED", example = "in")
    private String transferType;
    
    @JsonProperty("transferAmount")
    @Schema(description = "Tiền vào phải dương và nguyên VND; 100.00 hợp lệ, 100.10 ghi FAILED giữ số gốc", example = "100000")
    private BigDecimal transferAmount;
    
    @JsonProperty("accumulated")
    @Schema(description = "Số dư/tổng lũy kế provider; metadata, không cộng vào paidAmount")
    private BigDecimal accumulated;
    
    @JsonProperty("referenceCode")
    @Schema(description = "Định danh giao dịch ổn định dùng chống trùng; tối đa 100 ký tự; ưu tiên hơn id", maxLength = 100, example = "SEPAY-REF-123456")
    private String referenceCode;
    
    @JsonProperty("description")
    @Schema(description = "Mô tả bổ sung provider; mã order không đọc từ trường này")
    private String description;
}

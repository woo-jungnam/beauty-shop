package com.core.beautyshop.modules.procurement.api;
import com.core.beautyshop.modules.procurement.application.ProcurementReceiptService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/procurement/orders/{id}") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Nhập kho & Thanh toán mua hàng (Admin)", description = "Nhận hàng nhập kho, trả hàng nhà cung cấp, thanh toán và hoàn tiền đơn mua hàng")
public class ProcurementReceiptController {
    private final ProcurementReceiptService service;

    @Operation(summary = "Xem tổng quan tài chính đơn mua hàng (Admin)", description = "Tổng giá trị đơn hàng nhập, số tiền đã thanh toán, công nợ và số tiền đã hoàn")
    @GetMapping("/financials")public ApiResponse<?> financials(@PathVariable Long id){return ApiResponse.success(service.financials(id));}

    @Operation(summary = "Nhập kho đợt giao hàng mua (Admin/Staff)", description = "Ghi nhận số lượng hàng thực tế nhận từ nhà cung cấp và tăng tồn kho")
    @PostMapping("/receipts")public ApiResponse<?> receive(@PathVariable Long id,@RequestBody ProcurementReceiptService.Receipt c){return ApiResponse.success(service.receive(id,c));}

    @Operation(summary = "Trả hàng cho nhà cung cấp (Admin/Staff)", description = "Xuất trả hàng lỗi hoặc thừa về nhà cung cấp và giảm tồn kho tương ứng")
    @PostMapping("/supplier-returns")public ApiResponse<?> supplierReturn(@PathVariable Long id,@RequestBody ProcurementReceiptService.Receipt c){return ApiResponse.success(service.supplierReturn(id,c));}

    @Operation(summary = "Ghi nhận thanh toán cho nhà cung cấp (Admin)", description = "Ghi nhận phiếu chi thanh toán tiền mua hàng cho nhà cung cấp")
    @PostMapping("/payments") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> payment(@PathVariable Long id,@RequestBody ProcurementReceiptService.Payment c){return ApiResponse.success(service.payment(id,c,false));}

    @Operation(summary = "Ghi nhận nhà cung cấp hoàn tiền (Admin)", description = "Ghi nhận khoản tiền nhà cung cấp hoàn trả do hàng lỗi hoặc thừa")
    @PostMapping("/supplier-refunds") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> refund(@PathVariable Long id,@RequestBody ProcurementReceiptService.Payment c){return ApiResponse.success(service.payment(id,c,true));}
}

package com.core.beautyshop.modules.procurement.api;
import com.core.beautyshop.modules.procurement.application.ProcurementReceiptService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/procurement/orders/{id}") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ProcurementReceiptController {
 private final ProcurementReceiptService service;
 @GetMapping("/financials")public ApiResponse<?> financials(@PathVariable Long id){return ApiResponse.success(service.financials(id));}
 @PostMapping("/receipts")public ApiResponse<?> receive(@PathVariable Long id,@RequestBody ProcurementReceiptService.Receipt c){return ApiResponse.success(service.receive(id,c));}
 @PostMapping("/supplier-returns")public ApiResponse<?> supplierReturn(@PathVariable Long id,@RequestBody ProcurementReceiptService.Receipt c){return ApiResponse.success(service.supplierReturn(id,c));}
 @PostMapping("/payments") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> payment(@PathVariable Long id,@RequestBody ProcurementReceiptService.Payment c){return ApiResponse.success(service.payment(id,c,false));}
 @PostMapping("/supplier-refunds") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> refund(@PathVariable Long id,@RequestBody ProcurementReceiptService.Payment c){return ApiResponse.success(service.payment(id,c,true));}
}

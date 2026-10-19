package com.core.beautyshop.modules.order.api;
import com.core.beautyshop.modules.order.application.service.*;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/commerce") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminCommerceController {
    private final ProductReturnService returns;
    private final OrderCodSettlementService cod;
    @GetMapping("/orders/{id}/balances") public ApiResponse<?> balances(@PathVariable Long id){return ApiResponse.success(returns.balances(id));}
    @PostMapping("/orders/{id}/reallocate") public ApiResponse<?> reallocate(@PathVariable Long id){returns.replaceAllocation(id);return ApiResponse.success("Sellable replacement batches allocated");}
    @GetMapping("/returns") public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable pageable){return ApiResponse.success(PageResponse.of(returns.list(pageable)));}
    @PostMapping("/returns/{id}/decision") public ApiResponse<?> decide(@PathVariable Long id,@RequestBody ProductReturnService.Decision c){return ApiResponse.success(returns.decide(id,c));}
    @PostMapping("/returns/{id}/replacement")public ApiResponse<?> replacement(@PathVariable Long id,@RequestBody ProductReturnService.Replacement c){return ApiResponse.success(returns.linkReplacement(id,c));}
    @PostMapping("/returns/{id}/receive") public ApiResponse<?> receive(@PathVariable Long id,@RequestBody ProductReturnService.Confirmation c){return ApiResponse.success(returns.receive(id,c));}
    @PostMapping("/returns/{id}/refund") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> refund(@PathVariable Long id,@RequestBody ProductReturnService.Confirmation c){return ApiResponse.success(returns.refund(id,c));}
    public record Refund(BigDecimal amount,String reference,String reason){}
    @PostMapping("/orders/{id}/overpayment-refund") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> overpayment(@PathVariable Long id,@RequestBody Refund c){returns.overpaymentRefund(id,c.amount(),c.reference(),c.reason());return ApiResponse.success("Refund recorded");}
    @PostMapping("/orders/{id}/cod-settlement") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> settle(@PathVariable Long id,@RequestBody OrderCodSettlementService.Settlement c){cod.confirm(id,c);return ApiResponse.success("Carrier remittance recorded");}
}

package com.core.beautyshop.modules.order.api;
import com.core.beautyshop.modules.order.application.service.*;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import java.math.BigDecimal;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/commerce") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Thương mại & Đổi trả (Admin)", description = "Quản lý đổi trả hàng, quyết định đổi/trả/hoàn tiền, đối soát COD và hoàn tiền thừa")
public class AdminCommerceController {
    private final ProductReturnService returns;
    private final OrderCodSettlementService cod;

    @Operation(summary = "Kiểm tra số dư tài chính đơn hàng", description = "Xem chi tiết số tiền đã thanh toán, giá trị hoàn trả và số dư còn lại của đơn hàng")
    @GetMapping("/orders/{id}/balances") public ApiResponse<?> balances(@PathVariable Long id){return ApiResponse.success(returns.balances(id));}

    @Operation(summary = "Tái phân bổ lô hàng thay thế", description = "Chỉ định lô hàng mới còn khả dụng trong kho để thay thế sản phẩm bị trả lại")
    @PostMapping("/orders/{id}/reallocate") public ApiResponse<?> reallocate(@PathVariable Long id){returns.replaceAllocation(id);return ApiResponse.success("Sellable replacement batches allocated");}

    @Operation(summary = "Danh sách yêu cầu đổi trả hàng (Admin)", description = "Phân trang danh sách các yêu cầu đổi trả hàng từ khách hàng")
    @GetMapping("/returns") public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable pageable){return ApiResponse.success(PageResponse.of(returns.list(pageable)));}

    @Operation(summary = "Quyết định xử lý đổi trả (Staff/Admin)", description = "Chấp thuận hoặc từ chối yêu cầu đổi hàng/hoàn tiền của khách")
    @PostMapping("/returns/{id}/decision") public ApiResponse<?> decide(@PathVariable Long id,@RequestBody ProductReturnService.Decision c){return ApiResponse.success(returns.decide(id,c));}

    @Operation(summary = "Tạo đơn hàng đổi thế (Staff/Admin)", description = "Liên kết đơn hàng giao sản phẩm thay thế cho khách hàng")
    @PostMapping("/returns/{id}/replacement")public ApiResponse<?> replacement(@PathVariable Long id,@RequestBody ProductReturnService.Replacement c){return ApiResponse.success(returns.linkReplacement(id,c));}

    @Operation(summary = "Xác nhận đã nhận hàng trả về kho (Staff/Admin)", description = "Ghi nhận sản phẩm trả về đã nhập vào kho cách ly để kiểm định")
    @PostMapping("/returns/{id}/receive") public ApiResponse<?> receive(@PathVariable Long id,@RequestBody ProductReturnService.Confirmation c){return ApiResponse.success(returns.receive(id,c));}

    @Operation(summary = "Hoàn tiền cho yêu cầu trả hàng (Admin)", description = "Xác nhận hoàn tiền cho khách hàng sau khi kiểm định hàng trả đạt yêu cầu")
    @PostMapping("/returns/{id}/refund") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> refund(@PathVariable Long id,@RequestBody ProductReturnService.Confirmation c){return ApiResponse.success(returns.refund(id,c));}

    public record Refund(BigDecimal amount,String reference,String reason){}

    @Operation(summary = "Ghi nhận hoàn tiền thanh toán thừa (Admin)", description = "Ghi nhận chứng từ hoàn lại tiền cho khách hàng do thanh toán dư hoặc hủy một phần")
    @PostMapping("/orders/{id}/overpayment-refund") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> overpayment(@PathVariable Long id,@RequestBody Refund c){returns.overpaymentRefund(id,c.amount(),c.reference(),c.reason());return ApiResponse.success("Refund recorded");}

    @Operation(summary = "Đối soát tiền thu hộ COD từ đơn vị vận chuyển (Admin)", description = "Ghi nhận khoản tiền COD do hãng vận chuyển đối soát và chuyển về tài khoản")
    @PostMapping("/orders/{id}/cod-settlement") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> settle(@PathVariable Long id,@RequestBody OrderCodSettlementService.Settlement c){cod.confirm(id,c);return ApiResponse.success("Carrier remittance recorded");}
}

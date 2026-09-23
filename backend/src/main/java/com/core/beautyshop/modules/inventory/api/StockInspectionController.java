package com.core.beautyshop.modules.inventory.api;
import com.core.beautyshop.modules.inventory.application.service.StockInspectionService;
import com.core.beautyshop.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/inventory") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StockInspectionController {
    private final StockInspectionService inspection;
    public record Inspection(@Min(1) int quantity, boolean restock) {}
    @PostMapping("/{stockId}/inspection")
    public ApiResponse<String> inspect(@PathVariable Long stockId, @Valid @RequestBody Inspection request) {
        inspection.inspect(stockId, request.quantity(), request.restock());
        return ApiResponse.success("Inspection recorded");
    }
}

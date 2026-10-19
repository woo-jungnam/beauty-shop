package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaConsumableService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class SpaConsumableController {
 private final SpaConsumableService materials;
 @PreAuthorize("@staffPermissions.has('SPA_EXECUTION') or @staffPermissions.has('SPA_RECEPTION')")
 @GetMapping("/api/v1/spa/material-options") public ApiResponse<List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption>> options() { return ApiResponse.success(materials.options()); }
 @GetMapping("/api/v1/admin/spa/services/{id}/materials") public ApiResponse<List<SpaConsumableService.Material>> definitions(@PathVariable Long id) { return ApiResponse.success(materials.definitions(id)); }
 @PutMapping("/api/v1/admin/spa/services/{id}/materials") public ApiResponse<List<SpaConsumableService.Material>> save(@PathVariable Long id,@RequestBody List<SpaConsumableService.Material> rows) { return ApiResponse.success(materials.saveDefinitions(id,rows)); }
 @GetMapping("/api/v1/spa/appointments/{id}/materials") public ApiResponse<List<SpaConsumableService.ItemMaterials>> view(@PathVariable Long id) { return ApiResponse.success(materials.view(id)); }
 @PostMapping("/api/v1/spa/appointments/{id}/items/{itemId}/material-usages") public ApiResponse<List<SpaConsumableService.ItemMaterials>> consume(@PathVariable Long id,@PathVariable Long itemId,@RequestBody SpaConsumableService.UsageCommand c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(materials.consume(id,itemId,c,key)); }
 public record Waiver(String reason) {}
 @PostMapping("/api/v1/spa/appointments/{id}/items/{itemId}/material-waiver") public ApiResponse<List<SpaConsumableService.ItemMaterials>> waive(@PathVariable Long id,@PathVariable Long itemId,@RequestBody Waiver c) { return ApiResponse.success(materials.waive(id,itemId,c.reason())); }
}

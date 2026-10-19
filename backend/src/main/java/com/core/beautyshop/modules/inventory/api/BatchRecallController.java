package com.core.beautyshop.modules.inventory.api;
import com.core.beautyshop.modules.inventory.application.service.BatchRecallService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/inventory/recalls") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class BatchRecallController {
 private final BatchRecallService service;
 @GetMapping public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}
 @PostMapping public ApiResponse<?> create(@RequestBody BatchRecallService.Command c){return ApiResponse.success(service.recall(c));}
 @GetMapping("/{id}/customers")public ApiResponse<?> trace(@PathVariable Long id){return ApiResponse.success(service.trace(id));}
 @PostMapping("/{id}/outreach")public ApiResponse<?> contact(@PathVariable Long id,@RequestBody BatchRecallService.Contact c){return ApiResponse.success(service.contact(id,c));}
 @GetMapping("/{id}/outreach-history")public ApiResponse<?> history(@PathVariable Long id){return ApiResponse.success(service.history(id));}
 public record Closure(String reason){}
 @PostMapping("/{id}/close") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> close(@PathVariable Long id,@RequestBody Closure c){service.close(id,c.reason());return ApiResponse.success("Recall closed");}
}

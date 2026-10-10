package com.core.beautyshop.modules.procurement.application;

import com.core.beautyshop.modules.inventory.api.InventoryAdminFacade;
import com.core.beautyshop.modules.procurement.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProcurementService {
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final InventoryAdminFacade inventory;
    private final com.core.beautyshop.modules.catalog.api.CatalogFacade catalog;

    @Transactional(readOnly = true)
    public Page<SupplierView> suppliers(Pageable pageable) { return suppliers.findAll(pageable).map(this::supplierView); }

    @Transactional(readOnly = true)
    public SupplierView supplier(Long id) {
        return suppliers.findById(id).map(this::supplierView)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
        suppliers.delete(s);
    }

    @Transactional
    public SupplierView saveSupplier(Long id, SupplierCommand command) {
        if (command.code() == null || command.code().isBlank() || command.name() == null || command.name().isBlank())
            throw new BusinessException("Supplier code and name are required");
        Supplier supplier = id == null ? new Supplier() : suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        String normalizedCode = command.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicatedCode = id == null
                ? suppliers.existsByCodeIgnoreCase(normalizedCode)
                : suppliers.existsByCodeIgnoreCaseAndIdNot(normalizedCode, id);
        if (duplicatedCode) throw new BusinessException("Supplier code already exists");
        supplier.setCode(normalizedCode); supplier.setName(command.name().trim());
        supplier.setContactName(command.contactName()); supplier.setEmail(command.email()); supplier.setPhone(command.phone());
        supplier.setAddress(command.address()); supplier.setTaxCode(command.taxCode()); supplier.setActive(command.active() == null || command.active());
        return supplierView(suppliers.save(supplier));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderView> orders(PurchaseOrder.Status status, Pageable pageable) {
        if (status == null) return orders.findAll(pageable).map(this::orderView);
        return orders.findAll((root, query, cb) -> cb.equal(root.get("status"), status), pageable).map(this::orderView);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderView order(Long id) {
        return orders.findById(id).map(this::orderView)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    @Transactional
    public PurchaseOrderView create(OrderCommand command) {
        String orderNumber = "PO-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        PurchaseOrder order = PurchaseOrder.builder().orderNumber(orderNumber).status(PurchaseOrder.Status.DRAFT).build();
        applyCommand(order, command);
        return orderView(orders.save(order));
    }

    @Transactional
    public PurchaseOrderView update(Long id, OrderCommand command) {
        PurchaseOrder order = locked(id);
        if (order.getStatus() != PurchaseOrder.Status.DRAFT) throw new BusinessException("Only draft purchase orders can be edited");
        applyCommand(order, command);
        return orderView(order);
    }

    private void applyCommand(PurchaseOrder order, OrderCommand command) {
        if (command == null || command.supplierId() == null || command.warehouseId() == null
                || command.items() == null || command.items().isEmpty())
            throw new BusinessException("Supplier, warehouse and items are required");
        if (command.note() != null && command.note().length() > 500) throw new BusinessException("Note is too long");
        Supplier supplier = suppliers.findById(command.supplierId()).orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        validateSupplier(supplier);
        inventory.validateWarehouse(command.warehouseId());
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (ItemCommand item : command.items()) {
            if (item == null || item.variantId() == null || item.variantId() <= 0 || item.quantity() == null || item.quantity() <= 0
                    || item.unitCost() == null || item.unitCost().signum() < 0 || item.unitCost().scale() > 2)
                throw new BusinessException("Each purchase item requires a valid variant, quantity and cost");
            catalog.getVariantSummaryForInventory(item.variantId());
            if (item.batchCode() != null && item.batchCode().trim().length() > 100) throw new BusinessException("Batch code is too long");
            if (item.expirationDate() != null && !item.expirationDate().isAfter(LocalDate.now()))
                throw new BusinessException("Purchase item expiration date must be in the future");
            PurchaseOrderItem entity = PurchaseOrderItem.builder().purchaseOrder(order).productVariantId(item.variantId())
                    .quantity(item.quantity()).receivedQuantity(0).unitCost(item.unitCost()).batchCode(item.batchCode() == null ? "" : item.batchCode().trim())
                    .expirationDate(item.expirationDate()).build();
            items.add(entity);
            total = total.add(item.unitCost().multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setSupplier(supplier); order.setWarehouseId(command.warehouseId()); order.setExpectedDate(command.expectedDate()); order.setNote(command.note());
        order.getItems().clear(); order.getItems().addAll(items);
        order.setTotalAmount(total);
    }

    private void validateSupplier(Supplier supplier) {
        if (Boolean.TRUE.equals(supplier.getIsDeleted()) || !Boolean.TRUE.equals(supplier.getActive()))
            throw new BusinessException("Supplier is deleted or inactive");
    }

    private void validateReferences(PurchaseOrder order) {
        validateSupplier(order.getSupplier());
        inventory.validateWarehouse(order.getWarehouseId());
        for (PurchaseOrderItem item : order.getItems()) catalog.getVariantSummaryForInventory(item.getProductVariantId());
    }

    @Transactional
    public PurchaseOrderView approve(Long id) {
        PurchaseOrder order = locked(id);
        if (order.getStatus() != PurchaseOrder.Status.DRAFT) throw new BusinessException("Only draft purchase orders can be approved");
        validateReferences(order);
        order.setStatus(PurchaseOrder.Status.APPROVED); order.setApprovedAt(Instant.now());
        return orderView(order);
    }

    @Transactional
    public PurchaseOrderView receive(Long id) {
        PurchaseOrder order = locked(id);
        if (order.getStatus() != PurchaseOrder.Status.APPROVED) throw new BusinessException("Only approved purchase orders can be received");
        validateReferences(order);
        for (PurchaseOrderItem item : order.getItems()) {
            int remaining = item.getQuantity() - item.getReceivedQuantity();
            if (remaining <= 0) continue;
            inventory.receive(order.getWarehouseId(), item.getProductVariantId(), remaining, item.getUnitCost(),
                    item.getBatchCode(), item.getExpirationDate(), "PURCHASE_ORDER", order.getOrderNumber());
            item.setReceivedQuantity(item.getQuantity());
        }
        order.setStatus(PurchaseOrder.Status.RECEIVED); order.setReceivedAt(Instant.now());
        return orderView(order);
    }

    @Transactional
    public PurchaseOrderView cancel(Long id) {
        PurchaseOrder order = locked(id);
        if (order.getStatus() == PurchaseOrder.Status.RECEIVED) throw new BusinessException("Received purchase orders cannot be cancelled");
        order.setStatus(PurchaseOrder.Status.CANCELLED); return orderView(order);
    }

    private PurchaseOrder locked(Long id) { return orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order not found")); }
    private SupplierView supplierView(Supplier s) { return new SupplierView(s.getId(), s.getCode(), s.getName(), s.getContactName(), s.getEmail(), s.getPhone(), s.getAddress(), s.getTaxCode(), s.getActive()); }
    private PurchaseOrderView orderView(PurchaseOrder o) { return new PurchaseOrderView(o.getId(), o.getOrderNumber(), o.getSupplier().getId(), o.getSupplier().getName(), o.getWarehouseId(), o.getStatus(), o.getExpectedDate(), o.getTotalAmount(), o.getNote(), o.getApprovedAt(), o.getReceivedAt(), o.getCreatedAt(), o.getItems().stream().map(i -> new ItemView(i.getId(), i.getProductVariantId(), i.getQuantity(), i.getReceivedQuantity(), i.getUnitCost(), i.getBatchCode(), i.getExpirationDate())).toList()); }

    @Schema(description = "Tạo/thay thông tin nhà cung cấp; code/name kiểm bắt buộc tại service; trường tùy chọn thiếu được thay null/mặc định")
    public record SupplierCommand(
            @Schema(description = "Mã không trống, trim/viết hoa, duy nhất không phân biệt hoa thường", requiredMode = Schema.RequiredMode.REQUIRED, example = "SUP-001") String code,
            @Schema(description = "Tên không trống, trim", requiredMode = Schema.RequiredMode.REQUIRED, example = "Nhà cung cấp mỹ phẩm A") String name,
            String contactName, String email, String phone, String address, String taxCode,
            @Schema(description = "Cho phép dùng trong phiếu mua; thiếu/null mặc định true", defaultValue = "true") Boolean active) { }
    @Schema(description = "Thông tin nhà cung cấp; active=false không được dùng khi tạo/duyệt/nhận phiếu mua")
    public record SupplierView(Long id, String code, String name, String contactName, String email, String phone, String address, String taxCode, Boolean active) { }
    @Schema(description = "Dòng hàng dự kiến nhận; quantity/unitCost không phải giá bán cho khách")
    public record ItemCommand(
            @Schema(description = "ID SKU >0; SKU/product chưa xóa, có thể ngưng bán", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "201") Long variantId,
            @Schema(description = "Số lượng mua >0", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "10") Integer quantity,
            @Schema(description = "Giá vốn VND ≥0, tối đa 2 chữ số thập phân", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "100000.00") BigDecimal unitCost,
            @Schema(description = "Trim, tối đa 100 ký tự; null/trắng thành lô không mã", maxLength = 100, example = "LOT-001") String batchCode,
            @Schema(description = "YYYY-MM-DD; nếu có phải sau hôm nay", example = "2027-10-02") LocalDate expirationDate) { }
    @Schema(description = "Nội dung đầy đủ phiếu DRAFT; update thay tất cả dòng và tính lại tổng")
    public record OrderCommand(
            @Schema(description = "ID nhà cung cấp active/chưa xóa", requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long supplierId,
            @Schema(description = "ID kho active/chưa xóa", requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long warehouseId,
            @Schema(description = "Ngày dự kiến YYYY-MM-DD, tùy chọn; không phải hạn dùng lô", example = "2026-10-05") LocalDate expectedDate,
            @Schema(description = "Ghi chú tối đa 500 ký tự", maxLength = 500) String note,
            @ArraySchema(minItems = 1, arraySchema = @Schema(description = "Ít nhất một dòng hợp lệ", requiredMode = Schema.RequiredMode.REQUIRED), schema = @Schema(implementation = ItemCommand.class)) List<ItemCommand> items) { }
    @Schema(description = "Dòng phiếu mua; receive hiện nhận toàn bộ phần còn lại")
    public record ItemView(Long id, Long variantId, Integer quantity,
            @Schema(description = "Số đã nhập; sau receive bằng quantity") Integer receivedQuantity,
            BigDecimal unitCost, String batchCode, LocalDate expirationDate) { }
    @Schema(description = "Phiếu mua DRAFT/APPROVED/RECEIVED/CANCELLED; các Instant là UTC")
    public record PurchaseOrderView(Long id, String orderNumber, Long supplierId, String supplierName, Long warehouseId,
            PurchaseOrder.Status status, LocalDate expectedDate,
            @Schema(description = "Tổng giá vốn=sum(quantity×unitCost), VND") BigDecimal totalAmount,
            String note, Instant approvedAt, Instant receivedAt, Instant createdAt, List<ItemView> items) { }
}

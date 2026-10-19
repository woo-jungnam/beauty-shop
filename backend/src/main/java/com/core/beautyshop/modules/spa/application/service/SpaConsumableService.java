package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class SpaConsumableService {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final InventoryFacade inventory;
    private final SpaAccessService access;
    private final ObjectMapper json;
    public record Material(Long variantId,int quantity) {}
    public record UsageCommand(Long variantId,Long warehouseId,int quantity,String reason) {}
    public record Usage(Long id,Long appointmentItemId,String reference,Long variantId,Long warehouseId,int quantity,BigDecimal totalCost,String allocationJson,String reason,Long actorId,String createdAt) {}
    public record ItemMaterials(Long appointmentItemId,List<Material> planned,List<Usage> usages,String waiverReason) {}
    public List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption> options() { return inventory.listSpaMaterialOptions(); }

    @Transactional(readOnly=true)
    public List<Material> definitions(Long serviceId) {
        requireService(serviceId);
        return jdbc.query("SELECT variant_id,quantity FROM spa_service_materials WHERE service_id=? ORDER BY variant_id",(rs,n)->new Material(rs.getLong(1),rs.getInt(2)),serviceId);
    }
    @Transactional
    public List<Material> saveDefinitions(Long serviceId,List<Material> rows) {
        BeautyService service=requireService(serviceId);
        em.lock(service,LockModeType.PESSIMISTIC_WRITE);
        if(rows==null || rows.size()>100 || rows.stream().anyMatch(r->r==null || r.variantId()==null || r.variantId()<=0 || r.quantity()<=0)
                || rows.stream().map(Material::variantId).distinct().count()!=rows.size()) throw new BusinessException("Định mức vật tư phải có SKU không trùng và số lượng nguyên dương");
        for(Material row:rows) if(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=? AND is_deleted=FALSE",Long.class,row.variantId())!=1L)
            throw new ResourceNotFoundException("SKU vật tư không tồn tại hoặc đã ngưng sử dụng");
        jdbc.update("DELETE FROM spa_service_materials WHERE service_id=?",serviceId);
        for(Material row:rows) jdbc.update("INSERT INTO spa_service_materials(service_id,variant_id,quantity) VALUES (?,?,?)",serviceId,row.variantId(),row.quantity());
        return definitions(serviceId);
    }
    public void snapshot(Appointment appointment) {
        for(AppointmentItem item:appointment.getItems()) for(Material row:definitions(item.getService().getId()))
            jdbc.update("INSERT INTO spa_appointment_material_plans(appointment_item_id,variant_id,quantity) VALUES (?,?,?)",item.getId(),row.variantId(),row.quantity());
    }
    @Transactional(readOnly=true)
    public List<ItemMaterials> view(Long appointmentId) {
        Appointment a=appointment(appointmentId,false); access.requireCanViewAppointment(a);
        if(!access.canManageReception() && !(access.permission("SPA_EXECUTION") && access.isAssignedTechnician(a)))
            throw new org.springframework.security.access.AccessDeniedException("Sổ vật tư chỉ dành cho lễ tân hoặc người được giao thực hiện");
        return a.getItems().stream().map(item->new ItemMaterials(item.getId(),planned(item.getId()),usages(appointmentId,item.getId()),waiver(item.getId()))).toList();
    }
    @Transactional
    public List<ItemMaterials> consume(Long appointmentId,Long itemId,UsageCommand command,String reference) {
        Appointment a=appointment(appointmentId,true); AppointmentItem item=item(a,itemId); access.requireCanPerformItem(item);
        if(reference==null || reference.isBlank() || reference.length()>100 || command==null || command.variantId()==null || command.warehouseId()==null || command.quantity()<=0
                || command.reason()==null || command.reason().isBlank() || command.reason().length()>500) throw new BusinessException("Mã phiếu, SKU, kho, số lượng nguyên dương và lý do tối đa 500 ký tự là bắt buộc");
        String key=usageKey(itemId,reference);
        List<Usage> prior=jdbc.query("SELECT * FROM spa_material_usages WHERE reference=?",this::usage,key);
        if(!prior.isEmpty()) {
            Usage old=prior.getFirst();
            if(!Objects.equals(old.variantId(),command.variantId()) || !Objects.equals(old.warehouseId(),command.warehouseId()) || old.quantity()!=command.quantity() || !old.reason().equals(command.reason().trim()))
                throw new BusinessException("Mã phiếu vật tư đã được sử dụng với nội dung khác");
            return view(appointmentId);
        }
        if(a.getStatus()!=AppointmentStatus.IN_PROGRESS || item.getExecutionStatus()!=AppointmentItemExecutionStatus.IN_PROGRESS)
            throw new BusinessException("Chỉ ghi vật tư thực tế khi dịch vụ đang được thực hiện");
        var result=inventory.consumeSpaUsage(key,command.variantId(),command.warehouseId(),command.quantity());
        String allocations; try { allocations=json.writeValueAsString(result.batches()); } catch(Exception error) { throw new BusinessException("Không lưu được truy vết lô vật tư"); }
        jdbc.update("INSERT INTO spa_material_usages(appointment_id,appointment_item_id,reference,variant_id,warehouse_id,quantity,total_cost,allocation_json,reason,actor_id) VALUES (?,?,?,?,?,?,?,?,?,?)",appointmentId,itemId,key,command.variantId(),command.warehouseId(),command.quantity(),result.totalCost(),allocations,command.reason().trim(),SecurityUtils.getCurrentUserId());
        return view(appointmentId);
    }
    @Transactional
    public List<ItemMaterials> waive(Long appointmentId,Long itemId,String reason) {
        Appointment a=appointment(appointmentId,true); AppointmentItem item=item(a,itemId); access.requireCanPerformItem(item);
        if(a.getStatus()!=AppointmentStatus.IN_PROGRESS || item.getExecutionStatus()!=AppointmentItemExecutionStatus.IN_PROGRESS || reason==null || reason.isBlank() || reason.length()>500)
            throw new BusinessException("Dịch vụ phải đang thực hiện và có lý do không dùng vật tư (tối đa 500 ký tự)");
        if(waiver(itemId)==null) jdbc.update("INSERT INTO spa_material_usage_waivers(appointment_item_id,reason,actor_id) VALUES (?,?,?)",itemId,reason.trim(),SecurityUtils.getCurrentUserId());
        return view(appointmentId);
    }
    public void requireRecorded(AppointmentItem item) {
        if(!planned(item.getId()).isEmpty() && usages(item.getAppointment().getId(),item.getId()).isEmpty() && waiver(item.getId())==null)
            throw new BusinessException("Cần ghi vật tư thực tế hoặc lý do không sử dụng trước khi hoàn thành dịch vụ");
    }
    private List<Material> planned(Long id) { return jdbc.query("SELECT variant_id,quantity FROM spa_appointment_material_plans WHERE appointment_item_id=? ORDER BY variant_id",(rs,n)->new Material(rs.getLong(1),rs.getInt(2)),id); }
    private List<Usage> usages(Long appointmentId,Long itemId) { return jdbc.query("SELECT * FROM spa_material_usages WHERE appointment_id=? AND appointment_item_id=? ORDER BY id",this::usage,appointmentId,itemId); }
    private Usage usage(java.sql.ResultSet rs,int n) throws java.sql.SQLException { return new Usage(rs.getLong("id"),rs.getLong("appointment_item_id"),rs.getString("reference"),rs.getLong("variant_id"),rs.getLong("warehouse_id"),rs.getInt("quantity"),rs.getBigDecimal("total_cost"),rs.getString("allocation_json"),rs.getString("reason"),rs.getLong("actor_id"),rs.getTimestamp("created_at").toInstant().toString()); }
    private String waiver(Long id) { return jdbc.query("SELECT reason FROM spa_material_usage_waivers WHERE appointment_item_id=?",(rs,n)->rs.getString(1),id).stream().findFirst().orElse(null); }
    private Appointment appointment(Long id,boolean lock) { Appointment a=em.find(Appointment.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE); if(a==null || Boolean.TRUE.equals(a.getIsDeleted())) throw new ResourceNotFoundException("Không tìm thấy lịch hẹn"); return a; }
    private AppointmentItem item(Appointment a,Long id) { return a.getItems().stream().filter(i->Objects.equals(i.getId(),id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Không tìm thấy dịch vụ trong lịch")); }
    private BeautyService requireService(Long id) { BeautyService s=em.find(BeautyService.class,id); if(s==null || Boolean.TRUE.equals(s.getIsDeleted())) throw new ResourceNotFoundException("Không tìm thấy dịch vụ"); return s; }
    private static String usageKey(Long itemId,String reference) {
        try { return "SPA-"+itemId+"-"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(reference.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

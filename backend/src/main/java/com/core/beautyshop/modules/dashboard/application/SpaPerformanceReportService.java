package com.core.beautyshop.modules.dashboard.application;

import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** Earned service value uses historical invoices/entitlements/consumptions, never current catalog prices. */
@Service @RequiredArgsConstructor
public class SpaPerformanceReportService {
 private final JdbcTemplate jdbc;
 public record ServiceRow(Long serviceId,String serviceName,long performedItems,long performedMinutes,BigDecimal visitInvoiceRevenue,BigDecimal ordinaryPackageRevenue,BigDecimal treatmentRevenue,BigDecimal earnedRevenue,BigDecimal unbilledSnapshotValue,long unbilledItems,long missingRevenueItems,BigDecimal recordedMaterialCost,long missingPlannedMaterialItems,long untrackedMaterialItems,long materialWaiverItems,BigDecimal contributionAfterRecordedMaterials){}
 public record Report(Instant from,Instant to,List<ServiceRow> services,long performedItemsMissingTime,long excludedLegacyItems,String basis){}
 @Transactional(readOnly=true) public Report report(Instant from,Instant to){
  Instant end=to==null?Instant.now():to,start=from==null?end.minus(Duration.ofDays(30)):from;if(!start.isBefore(end))throw new BusinessException("From must precede to");
  Map<Long,Bucket> services=new LinkedHashMap<>();
  var rows=jdbc.queryForList("""
    SELECT ai.id,ai.service_id,COALESCE(ai.service_name_snapshot,s.name) service_name,ai.ticket_id,ai.treatment_session_item_id,
      ai.price,ai.actual_started_at,ai.actual_completed_at,ai.ticket_usage_state,
      f.kind consumption_kind,f.amount treatment_amount,
      vi.unit_price invoice_price,inv.total_amount invoice_total,
      (SELECT COALESCE(SUM(r.amount),0) FROM refund_confirmations r WHERE r.order_id=inv.id AND r.refund_kind<>'OVERPAYMENT') invoice_refunded,
      po.total_amount package_total,po.paid_amount package_paid,po.refunded_amount package_refunded,po.paid_at package_paid_at,
      (SELECT SUM(e.quantity) FROM spa_purchase_entitlements e WHERE e.order_id=t.order_id) original_rights,
      (SELECT e.quantity FROM spa_purchase_entitlements e WHERE e.order_id=t.order_id AND e.service_id=ai.service_id) original_service_rights,
      (SELECT COUNT(*) FROM appointment_items x WHERE x.ticket_id=ai.ticket_id AND x.is_deleted=false AND x.execution_status='PERFORMED' AND x.ticket_usage_state='REDEEMED') performed_rights,
      (SELECT COALESCE(SUM(u.total_cost),0) FROM spa_material_usages u WHERE u.appointment_item_id=ai.id) material_cost,
      (SELECT COUNT(*) FROM spa_material_usages u WHERE u.appointment_item_id=ai.id) material_usage_count,
      (SELECT COUNT(*) FROM spa_appointment_material_plans p WHERE p.appointment_item_id=ai.id) material_plan_count,
      (SELECT COUNT(*) FROM spa_appointment_material_plans p WHERE p.appointment_item_id=ai.id AND p.quantity>COALESCE((SELECT SUM(u.quantity) FROM spa_material_usages u WHERE u.appointment_item_id=ai.id AND u.variant_id=p.variant_id),0)) missing_materials,
      (SELECT COUNT(*) FROM spa_material_usage_waivers w WHERE w.appointment_item_id=ai.id) material_waiver
    FROM appointment_items ai JOIN appointments a ON a.id=ai.appointment_id JOIN beauty_services s ON s.id=ai.service_id
    LEFT JOIN treatment_financial_consumptions f ON f.appointment_item_id=ai.id
    LEFT JOIN spa_visit_invoice_items vi ON vi.appointment_item_id=ai.id AND vi.is_deleted=false
    LEFT JOIN orders inv ON inv.id=vi.order_id AND inv.is_deleted=false
    LEFT JOIN user_service_tickets t ON t.id=ai.ticket_id
    LEFT JOIN orders po ON po.id=t.order_id AND po.is_deleted=false
    WHERE ai.is_deleted=false AND a.is_deleted=false AND ai.execution_status='PERFORMED'
      AND ai.actual_completed_at>=? AND ai.actual_completed_at<? ORDER BY ai.service_id,ai.id
    """,Timestamp.from(start),Timestamp.from(end));
  for(var row:rows){
   if("FORFEIT".equals(row.get("consumption_kind"))||"FORFEITED".equals(row.get("ticket_usage_state")))continue;
   long id=number(row.get("service_id"));var b=services.computeIfAbsent(id,key->new Bucket(key,String.valueOf(row.get("service_name"))));b.performed++;
   Instant began=instant(row.get("actual_started_at")),completed=instant(row.get("actual_completed_at"));if(began!=null&&completed!=null)b.minutes+=Math.max(0,Duration.between(began,completed).toMinutes());
   if(row.get("treatment_session_item_id")!=null||row.get("consumption_kind")!=null){
    if("PERFORMED".equals(row.get("consumption_kind"))&&row.get("treatment_amount")!=null)b.treatment=b.treatment.add(decimal(row.get("treatment_amount")));else b.missingRevenue++;
   }else if(row.get("ticket_id")==null){
    if(row.get("invoice_price")==null||row.get("invoice_total")==null){b.unbilled++;b.unbilledValue=b.unbilledValue.add(decimal(row.get("price")));}
    else {BigDecimal total=decimal(row.get("invoice_total"));BigDecimal amount=decimal(row.get("invoice_price"));if(total.signum()>0)amount=amount.multiply(total.subtract(decimal(row.get("invoice_refunded"))).max(BigDecimal.ZERO)).divide(total,2,RoundingMode.DOWN);b.visit=b.visit.add(amount);}
   }else {
    long rights=number(row.get("original_rights")),serviceRights=number(row.get("original_service_rights"));
    if(rights<=0||serviceRights<=0||row.get("package_paid_at")==null||row.get("package_total")==null||!"REDEEMED".equals(row.get("ticket_usage_state")))b.missingRevenue++;
    else {
     BigDecimal perRight=decimal(row.get("package_total")).divide(BigDecimal.valueOf(rights),2,RoundingMode.DOWN);
     long performed=Math.max(1,number(row.get("performed_rights")));BigDecimal remainingPaid=decimal(row.get("package_paid")).subtract(decimal(row.get("package_refunded"))).max(BigDecimal.ZERO);
     b.ordinary=b.ordinary.add(perRight.min(remainingPaid.divide(BigDecimal.valueOf(performed),2,RoundingMode.DOWN)));
    }
   }
   b.materialCost=b.materialCost.add(decimal(row.get("material_cost")));boolean waived=number(row.get("material_waiver"))>0;
   if(waived)b.waivers++;else if(number(row.get("missing_materials"))>0)b.missingMaterial++;
   else if(number(row.get("material_plan_count"))==0&&number(row.get("material_usage_count"))==0)b.untracked++;
  }
  long missing=jdbc.queryForObject("SELECT COUNT(*) FROM appointment_items WHERE is_deleted=false AND execution_status='PERFORMED' AND actual_completed_at IS NULL",Long.class);
  long legacy=jdbc.queryForObject("SELECT COUNT(*) FROM appointment_items WHERE is_deleted=false AND execution_status='LEGACY_FINALIZED'",Long.class);
  return new Report(start,end,services.values().stream().map(Bucket::view).toList(),missing,legacy,"Performed items by actual completion in [from,to). Visit revenue uses invoice snapshots net of current refunds; unbilled performed value is separate. Ordinary package price is allocated equally per original entitled session and capped by retained receipts across performed rights, without using current package prices. Treatment value comes only from PERFORMED financial-consumption rows; FORFEIT is excluded and never counted twice. Material contribution subtracts actual item-linked usage costs, excludes wages/commissions/equipment/overheads, and is withheld when revenue or material evidence is incomplete. Refund adjustments reflect current state.");
 }
 private static long number(Object value){return value instanceof Number n?n.longValue():0;}
 private static Instant instant(Object v){return v instanceof Timestamp t?t.toInstant():v instanceof OffsetDateTime t?t.toInstant():v instanceof Instant t?t:v instanceof LocalDateTime t?t.toInstant(ZoneOffset.UTC):null;}
 private static BigDecimal decimal(Object value){return value instanceof BigDecimal b?b:value instanceof Number n?BigDecimal.valueOf(n.doubleValue()):BigDecimal.ZERO;}
 private static final class Bucket{
  final long id;final String name;long performed,minutes,unbilled,missingRevenue,missingMaterial,untracked,waivers;BigDecimal visit=BigDecimal.ZERO,ordinary=BigDecimal.ZERO,treatment=BigDecimal.ZERO,unbilledValue=BigDecimal.ZERO,materialCost=BigDecimal.ZERO;
  Bucket(long id,String name){this.id=id;this.name=name;}
  ServiceRow view(){BigDecimal earned=visit.add(ordinary).add(treatment);return new ServiceRow(id,name,performed,minutes,visit,ordinary,treatment,earned,unbilledValue,unbilled,missingRevenue,materialCost,missingMaterial,untracked,waivers,unbilled+missingRevenue+missingMaterial+untracked==0?earned.subtract(materialCost):null);}
 }
}

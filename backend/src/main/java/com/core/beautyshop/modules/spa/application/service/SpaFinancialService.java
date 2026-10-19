package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

/** Confirmed receipts and refund evidence; this service never initiates a bank transfer. */
@Service @RequiredArgsConstructor
public class SpaFinancialService {
 private final JdbcTemplate jdbc;
 private final EntityManager em;
 private final SpaAccessService access;
 private final OrderFacade orders;
 public record Policy(int version,BigDecimal depositAmount,BigDecimal cancellationFee,BigDecimal noShowFee) {}
 public record Entry(Long id,String key,String kind,BigDecimal amount,String method,String externalReference,String reason,Long actorId,String createdAt) {}
 public record Finance(Long appointmentId,int policyVersion,BigDecimal depositRequired,BigDecimal depositCollected,BigDecimal appliedToInvoice,
                       BigDecimal assessedFee,BigDecimal feePaid,BigDecimal feeDue,BigDecimal refundable,BigDecimal refunded,boolean closed,List<Entry> entries) {}
 public record Receipt(BigDecimal amount,String method,String externalReference,String reason) {}
 @Transactional(readOnly=true)
 public Policy policy() { return jdbc.queryForObject("SELECT * FROM spa_financial_policy WHERE id=1",(rs,n)->new Policy(rs.getInt("version"),rs.getBigDecimal("deposit_amount"),rs.getBigDecimal("cancellation_fee"),rs.getBigDecimal("no_show_fee"))); }
 @Transactional
 public Policy savePolicy(Policy value) {
  whole(value.depositAmount()); whole(value.cancellationFee()); whole(value.noShowFee());
  Integer revision=jdbc.queryForObject("SELECT version FROM spa_financial_policy WHERE id=1 FOR UPDATE",Integer.class);
  if(value.version()!=revision) throw new BusinessException("Chính sách đã thay đổi, vui lòng tải lại");
  jdbc.update("UPDATE spa_financial_policy SET version=version+1,deposit_amount=?,cancellation_fee=?,no_show_fee=? WHERE id=1",value.depositAmount(),value.cancellationFee(),value.noShowFee()); return policy();
 }
 public void snapshot(Appointment appointment,Boolean accepted,Integer version) {
  Policy p=policy();
  boolean treatment=appointment.getItems().stream().anyMatch(i->i.getTreatmentSessionItemId()!=null);
  BigDecimal payable=appointment.getItems().stream().filter(i->i.getTicket()==null).map(AppointmentItem::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(0,RoundingMode.HALF_UP);
  boolean enabled=!treatment && (p.depositAmount().signum()>0 || p.cancellationFee().signum()>0 || p.noShowFee().signum()>0);
  if(enabled && (!Boolean.TRUE.equals(accepted) || version==null || version!=p.version())) throw new BusinessException("Cần xác nhận đúng phiên bản chính sách cọc, hủy và vắng hẹn trước khi đặt");
  jdbc.update("INSERT INTO spa_appointment_finance(appointment_id,policy_version,deposit_required,cancellation_fee,no_show_fee,accepted_by,accepted_at) VALUES(?,?,?,?,?,?,?)",
          appointment.getId(),p.version(),treatment?BigDecimal.ZERO:p.depositAmount().min(payable),treatment?BigDecimal.ZERO:p.cancellationFee(),treatment?BigDecimal.ZERO:p.noShowFee(),enabled?SecurityUtils.getCurrentUserId():null,enabled?java.sql.Timestamp.from(Instant.now()):null);
 }
 public void requireDeposit(Appointment appointment) {
  Finance f=load(appointment.getId());
  if(f.depositCollected().compareTo(f.depositRequired())<0) throw new BusinessException("Chưa thu đủ tiền cọc theo chính sách đã xác nhận");
 }
 public void close(Appointment appointment,boolean noShow,boolean chargeCancellation) {
  jdbc.update("UPDATE spa_appointment_finance SET closed=TRUE,assessed_fee="+(noShow?"no_show_fee":chargeCancellation?"cancellation_fee":"0")+" WHERE appointment_id=?",appointment.getId());
 }
 @Transactional(readOnly=true)
 public Finance view(Long id) { Appointment a=appointment(id,false); access.requireCanViewAppointment(a); return load(id); }
 @Transactional
 public Finance receipt(Long id,Receipt c,String key,boolean fee) {
  Appointment a=appointment(id,true); if(!access.canManageReception()) throw new org.springframework.security.access.AccessDeniedException("Chỉ lễ tân được ghi phiếu thu");
  validate(c,key); Finance f=load(id); String kind=fee?"FEE":"DEPOSIT";
  if(replay(id,key,kind,c)) return load(id);
  if(fee) { if(!f.closed() || c.amount().compareTo(f.feeDue())>0) throw new BusinessException("Số thu vượt phí còn thiếu hoặc lịch chưa đóng"); }
  else if(!Set.of(AppointmentStatus.PENDING,AppointmentStatus.CONFIRMED).contains(a.getStatus()) || c.amount().compareTo(f.depositRequired().subtract(f.depositCollected()))>0)
   throw new BusinessException("Chỉ thu phần cọc còn thiếu trước khi thực hiện dịch vụ");
  insert(id,key,kind,c); return load(id);
 }
 @Transactional
 public Finance refund(Long id,Receipt c,String key) {
  appointment(id,true); if(!access.hasRole("ADMIN")) throw new org.springframework.security.access.AccessDeniedException("Chỉ quản trị xác nhận hoàn cọc");
  validate(c,key); Finance f=load(id);
  if(replay(id,key,"REFUND",c)) return f;
  if(!f.closed() || c.amount().compareTo(f.refundable())>0) throw new BusinessException("Số hoàn vượt phần cọc phải hoàn hoặc buổi chưa chốt");
  insert(id,key,"REFUND",c); return load(id);
 }
 public void applyAdvance(Appointment appointment,BigDecimal invoiceTotal) {
  Finance f=load(appointment.getId());
  BigDecimal remaining=f.depositCollected().subtract(f.appliedToInvoice()).subtract(f.refunded()).max(BigDecimal.ZERO);
  BigDecimal applicable=remaining.min(invoiceTotal.subtract(f.appliedToInvoice()).max(BigDecimal.ZERO));
  if(applicable.signum()>0) {
   String key="SPA-ADVANCE-"+appointment.getId();
   orders.applySpaVisitAdvance(appointment.getId(),applicable,key);
   insert(appointment.getId(),key,"APPLY",new Receipt(applicable,"INTERNAL",key,"Phân bổ tiền cọc đã thực nhận vào hóa đơn buổi hẹn"));
  }
  jdbc.update("UPDATE spa_appointment_finance SET closed=TRUE WHERE appointment_id=?",appointment.getId());
 }
 public Map<String,BigDecimal> report() {
  var totals=new LinkedHashMap<String,BigDecimal>(); for(String kind:List.of("DEPOSIT","FEE","REFUND","APPLY"))
   totals.put(kind,jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM spa_financial_entries WHERE kind=?",BigDecimal.class,kind));
  totals.put("ASSESSED_FEES",jdbc.queryForObject("SELECT COALESCE(SUM(assessed_fee),0) FROM spa_appointment_finance",BigDecimal.class)); return totals;
 }
 private Finance load(Long id) {
  List<Entry> entries=jdbc.query("SELECT * FROM spa_financial_entries WHERE appointment_id=? ORDER BY id",(rs,n)->new Entry(rs.getLong("id"),rs.getString("entry_key"),rs.getString("kind"),rs.getBigDecimal("amount"),rs.getString("method"),rs.getString("external_reference"),rs.getString("reason"),rs.getLong("actor_id"),rs.getTimestamp("created_at").toInstant().toString()),id);
  BigDecimal collected=sum(entries,"DEPOSIT"),applied=sum(entries,"APPLY"),refunded=sum(entries,"REFUND"),feePaid=sum(entries,"FEE");
  var rows=jdbc.query("SELECT * FROM spa_appointment_finance WHERE appointment_id=?",(rs,n)-> {
   BigDecimal fee=rs.getBigDecimal("assessed_fee");
   return new Finance(id,rs.getInt("policy_version"),rs.getBigDecimal("deposit_required"),collected,applied,fee,feePaid,
           fee.subtract(collected.min(fee)).subtract(feePaid).max(BigDecimal.ZERO),collected.subtract(applied).subtract(collected.min(fee)).subtract(refunded).max(BigDecimal.ZERO),refunded,rs.getBoolean("closed"),entries);
  },id);
  return rows.isEmpty()?new Finance(id,0,BigDecimal.ZERO,collected,applied,BigDecimal.ZERO,feePaid,BigDecimal.ZERO,BigDecimal.ZERO,refunded,false,entries):rows.getFirst();
 }
 private boolean replay(Long id,String key,String kind,Receipt c) {
  List<String> existing=jdbc.query("SELECT entry_key FROM spa_financial_entries WHERE entry_key=?",(rs,n)->rs.getString(1),key);
  if(existing.isEmpty()) return false;
  boolean match=load(id).entries().stream().anyMatch(e->e.key().equals(key)&&e.kind().equals(kind)&&e.amount().compareTo(c.amount())==0&&e.method().equals(c.method())&&Objects.equals(e.externalReference(),c.externalReference())&&e.reason().equals(c.reason().trim()));
  if(!match) throw new BusinessException("Mã phiếu đã được dùng với nội dung khác"); return true;
 }
 private void insert(Long id,String key,String kind,Receipt c) { jdbc.update("INSERT INTO spa_financial_entries(appointment_id,entry_key,kind,amount,method,external_reference,reason,actor_id) VALUES(?,?,?,?,?,?,?,?)",id,key,kind,c.amount(),c.method(),c.externalReference(),c.reason().trim(),SecurityUtils.getCurrentUserId()); }
 private void validate(Receipt c,String key) {
  if(c==null) throw new BusinessException("Thông tin phiếu thu/hoàn là bắt buộc"); whole(c.amount());
  if(c.amount().signum()<=0 || key==null || key.isBlank() || key.length()>128 || !Set.of("CASH","BANK").contains(c.method()) || c.reason()==null || c.reason().isBlank() || c.reason().length()>500 || c.externalReference()==null || c.externalReference().isBlank() || c.externalReference().length()>128)
   throw new BusinessException("Cần số tiền nguyên dương, mã phiếu, CASH/BANK, mã đối chiếu thực tế và lý do");
  if("BANK".equals(c.method()) && jdbc.queryForObject("SELECT COUNT(*) FROM spa_financial_entries WHERE method='BANK' AND external_reference=? AND entry_key<>?",Long.class,c.externalReference(),key)>0)
   throw new BusinessException("Mã giao dịch ngân hàng đã được đối chiếu ở phiếu khác");
 }
 private static void whole(BigDecimal n) { if(n==null || n.signum()<0 || n.stripTrailingZeros().scale()>0 || n.compareTo(new BigDecimal("99999999"))>0) throw new BusinessException("Số tiền phải là VND nguyên từ 0 đến 99.999.999"); }
 private static BigDecimal sum(List<Entry> entries,String kind) { return entries.stream().filter(e->e.kind().equals(kind)).map(Entry::amount).reduce(BigDecimal.ZERO,BigDecimal::add); }
 private Appointment appointment(Long id,boolean lock) { Appointment a=em.find(Appointment.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE); if(a==null || Boolean.TRUE.equals(a.getIsDeleted())) throw new ResourceNotFoundException("Không tìm thấy lịch hẹn"); return a; }
}

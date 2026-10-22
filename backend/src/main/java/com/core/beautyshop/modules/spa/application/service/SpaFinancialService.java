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

@Service @RequiredArgsConstructor
public class SpaFinancialService implements com.core.beautyshop.modules.spa.api.SpaDepositFacade {
 @org.springframework.beans.factory.annotation.Autowired
 private com.core.beautyshop.modules.payment.api.PaymentFacade payments;
 private final JdbcTemplate jdbc;
 private final EntityManager em;
 private final SpaAccessService access;
 private final OrderFacade orders;
 public record Policy(int version,BigDecimal depositAmount,BigDecimal cancellationFee,BigDecimal noShowFee) {}
 public record Entry(Long id,String key,String kind,BigDecimal amount,String method,String externalReference,String reason,Long actorId,String createdAt) {}
 public record Finance(Long appointmentId,int policyVersion,BigDecimal depositRequired,BigDecimal depositCollected,BigDecimal appliedToInvoice,
                       BigDecimal assessedFee,BigDecimal feePaid,BigDecimal feeDue,BigDecimal refundable,BigDecimal refunded,boolean closed,List<Entry> entries,
                       BigDecimal depositDue,DepositPayment depositPayment,List<String> reviewReasons) {}
 public record DepositPayment(String transferCode,boolean canPay,com.core.beautyshop.modules.payment.api.dto.PaymentInstruction paymentInstruction) {}
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
 public Finance view(Long id) { Appointment a=appointment(id,false); requireFinanceAccess(a); return load(id); }
 private void requireFinanceAccess(Appointment a) {
  if(!access.canManageReception() && !Objects.equals(a.getUserId(),SecurityUtils.getCurrentUserId()))
   throw new org.springframework.security.access.AccessDeniedException("Chỉ chủ lịch hoặc thu ngân được xem tài chính");
 }
 @Transactional
 public Finance depositPayment(Long id) {
  Appointment a=appointment(id,true); requireFinanceAccess(a); Finance f=load(id);
  if(!canPay(a,f.closed(),f.depositDue())) throw new BusinessException("Lịch không còn cần thanh toán cọc");
  jdbc.update("UPDATE spa_appointment_finance SET deposit_transfer_code=? WHERE appointment_id=? AND deposit_transfer_code IS NULL",
          "SPADEP-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT),id);
  return load(id);
 }
 private boolean canPay(Appointment a,boolean closed,BigDecimal due) {
  return !closed && due.signum()>0 && Set.of(AppointmentStatus.PENDING,AppointmentStatus.CONFIRMED).contains(a.getStatus());
 }
 @Override @Transactional
 public String receiveDeposit(String transferCode,com.core.beautyshop.modules.payment.api.dto.BankReceipt receipt) {
  whole(receipt.amount());
  var ids=jdbc.query("SELECT appointment_id FROM spa_appointment_finance WHERE deposit_transfer_code=?",(rs,n)->rs.getLong(1),transferCode);
  if(ids.isEmpty()) return "DEPOSIT_REVIEW";
  Long id=ids.getFirst(); Appointment a=em.find(Appointment.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(a==null) return "DEPOSIT_REVIEW";
  em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
  var linked=jdbc.query("SELECT appointment_id FROM spa_financial_entries WHERE payment_transaction_id=?",(rs,n)->rs.getLong(1),receipt.transactionId());
  if(!linked.isEmpty()) {
   if(!id.equals(linked.getFirst())) throw new BusinessException("Giao dịch đã thuộc lịch khác");
   return depositStatus(load(id),a);
  }
  var evidence=jdbc.query("SELECT id,appointment_id,kind,amount FROM spa_financial_entries WHERE method='BANK' AND external_reference=?",(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4)},receipt.reference());
  if(!evidence.isEmpty()) {
   var old=evidence.getFirst();
   if(!id.equals(old[1]) || !"DEPOSIT".equals(old[2]) || receipt.amount().compareTo((BigDecimal)old[3])!=0) return "DEPOSIT_REVIEW";
   jdbc.update("UPDATE spa_financial_entries SET payment_transaction_id=? WHERE id=?",receipt.transactionId(),old[0]);
  } else {
   Long legacy=jdbc.queryForObject("SELECT COUNT(*) FROM spa_financial_entries WHERE appointment_id=? AND method='BANK' AND kind IN ('DEPOSIT','FEE') AND payment_transaction_id IS NULL",Long.class,id);
   if(legacy>0) return "DEPOSIT_REVIEW";
   jdbc.update("INSERT INTO spa_financial_entries(appointment_id,entry_key,kind,amount,method,external_reference,reason,actor_id,payment_transaction_id) VALUES(?,?,'DEPOSIT',?,'BANK',?,?,NULL,?)",
           id,"SEPAY-DEPOSIT-"+receipt.transactionId(),receipt.amount(),receipt.reference(),"Tiền cọc thực nhận qua SePay",receipt.transactionId());
  }
  return depositStatus(load(id),a);
 }
 private String depositStatus(Finance f,Appointment a) {
  if(f.closed() || Boolean.TRUE.equals(a.getIsDeleted()) || !f.reviewReasons().isEmpty()) return "DEPOSIT_REVIEW";
  return f.depositDue().signum()>0?"PARTIALLY_PAID":"SUCCESS";
 }
 @Transactional
 public Finance receipt(Long id,Receipt c,String key,boolean fee) {
  if(!access.canManageReception()) throw new org.springframework.security.access.AccessDeniedException("Chỉ lễ tân được ghi phiếu thu");
  if(c!=null && "BANK".equals(c.method())) return bankReceipt(id,c,key,fee);
  Appointment a=appointment(id,true);
  validate(c,key); Finance f=load(id); String kind=fee?"FEE":"DEPOSIT";
  if(replay(id,key,kind,c)) return load(id);
  if(fee) { if(!f.closed() || c.amount().compareTo(f.feeDue())>0) throw new BusinessException("Số thu vượt phí còn thiếu hoặc lịch chưa đóng"); }
  else if(!Set.of(AppointmentStatus.PENDING,AppointmentStatus.CONFIRMED).contains(a.getStatus()) || c.amount().compareTo(f.depositRequired().subtract(f.depositCollected()))>0)
   throw new BusinessException("Chỉ thu phần cọc còn thiếu trước khi thực hiện dịch vụ");
  insert(id,key,kind,c); return load(id);
 }
 private Finance bankReceipt(Long id,Receipt c,String key,boolean fee) {
  if(c.externalReference()==null || c.externalReference().isBlank()) throw new BusinessException("Cần mã giao dịch SePay");
  var receipt=payments.lockBankReceipt(c.externalReference().trim());
  Appointment a=appointment(id,true); String kind=fee?"FEE":"DEPOSIT";
  var linked=jdbc.query("SELECT appointment_id,kind FROM spa_financial_entries WHERE payment_transaction_id=?",(rs,n)->Map.entry(rs.getLong(1),rs.getString(2)),receipt.transactionId());
  if(!linked.isEmpty()) {
   if(!id.equals(linked.getFirst().getKey()) || !kind.equals(linked.getFirst().getValue()) || c.amount()==null || c.amount().compareTo(receipt.amount())!=0)
    throw new BusinessException("Giao dịch đã được ghi nhận với nội dung khác");
   return load(id);
  }
  validate(c,key);
  if(c.amount().compareTo(receipt.amount())!=0) throw new BusinessException("Số tiền phải khớp toàn bộ giao dịch SePay");
  String code=jdbc.queryForObject("SELECT deposit_transfer_code FROM spa_appointment_finance WHERE appointment_id=?",String.class,id);
  if(code==null) {
   code="SPADEP-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
   jdbc.update("UPDATE spa_appointment_finance SET deposit_transfer_code=? WHERE appointment_id=?",code,id);
  }
  if(!"UNKNOWN".equals(receipt.target()) && !code.equals(receipt.target())) throw new BusinessException("Giao dịch có mã đích khác hoặc mơ hồ");
  Finance f=load(id);
  if(fee && (!f.closed() || c.amount().compareTo(f.feeDue())>0)) throw new BusinessException("Số thu vượt phí còn thiếu hoặc lịch chưa đóng");
  if(replay(id,key,kind,c)) return load(id);
  insert(id,key,kind,c);
  jdbc.update("UPDATE spa_financial_entries SET payment_transaction_id=? WHERE entry_key=?",receipt.transactionId(),key);
  payments.assignBankReceipt(receipt.transactionId(),code,fee?"SUCCESS":depositStatus(load(id),a));
  return load(id);
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
  if(f.closed()) return;
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
  List<Entry> entries=jdbc.query("SELECT * FROM spa_financial_entries WHERE appointment_id=? ORDER BY id",(rs,n)->new Entry(rs.getLong("id"),rs.getString("entry_key"),rs.getString("kind"),rs.getBigDecimal("amount"),rs.getString("method"),rs.getString("external_reference"),rs.getString("reason"),rs.getObject("actor_id",Long.class),rs.getTimestamp("created_at").toInstant().toString()),id);
  BigDecimal collected=sum(entries,"DEPOSIT"),applied=sum(entries,"APPLY"),refunded=sum(entries,"REFUND"),feePaid=sum(entries,"FEE");
  var rows=jdbc.query("SELECT * FROM spa_appointment_finance WHERE appointment_id=?",(rs,n)-> {
   BigDecimal fee=rs.getBigDecimal("assessed_fee"),required=rs.getBigDecimal("deposit_required"),due=required.subtract(collected).max(BigDecimal.ZERO);
   boolean closed=rs.getBoolean("closed"); String code=rs.getString("deposit_transfer_code");
   var reasons=new ArrayList<String>();
   if(collected.compareTo(required)>0) reasons.add("OVERPAYMENT");
   if(closed && collected.subtract(applied).subtract(refunded).signum()>0) reasons.add(applied.signum()>0?"UNALLOCATED_AFTER_INVOICE":"LATE_RECEIPT");
   boolean legacy=jdbc.queryForObject("SELECT COUNT(*) FROM spa_financial_entries WHERE appointment_id=? AND method='BANK' AND kind IN ('DEPOSIT','FEE') AND payment_transaction_id IS NULL",Long.class,id)>0;
   if(legacy) reasons.add("LEGACY_BANK_REVIEW");
   DepositPayment payment=null;
   if(code!=null) {
    Appointment a=em.find(Appointment.class,id);
    boolean payable=a!=null && !Boolean.TRUE.equals(a.getIsDeleted()) && canPay(a,closed,due);
    var instruction=payable?payments.processPayment(com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK,
            com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto.builder().orderNumber(code).totalAmount(due).build()):null;
    payment=new DepositPayment(code,payable,instruction);
   }
   return new Finance(id,rs.getInt("policy_version"),required,collected,applied,fee,feePaid,
           fee.subtract(collected.min(fee)).subtract(feePaid).max(BigDecimal.ZERO),collected.subtract(applied).subtract(collected.min(fee)).subtract(refunded).max(BigDecimal.ZERO),refunded,closed,entries,due,payment,reasons);
  },id);
  return rows.isEmpty()?new Finance(id,0,BigDecimal.ZERO,collected,applied,BigDecimal.ZERO,feePaid,BigDecimal.ZERO,BigDecimal.ZERO,refunded,false,entries,BigDecimal.ZERO,null,List.of()):rows.getFirst();
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

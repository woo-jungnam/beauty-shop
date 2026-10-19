package com.core.beautyshop.modules.dashboard.application;
import com.core.beautyshop.modules.dashboard.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProfitabilityService {
 private final JdbcTemplate jdbc;private final OperatingExpenseRepository expenses;
 public record Expense(String reference,String category,String description,BigDecimal amount,Instant incurredAt){}
 public record Report(Instant from,Instant to,BigDecimal deliveredProductNetSales,BigDecimal knownProductCost,Long allocationsMissingCost,Long legacyDeliveriesMissingTime,BigDecimal productGrossProfit,BigDecimal operatingExpenses,BigDecimal supplierPayments,BigDecimal supplierRecoveries,BigDecimal unassignedReceiptRefunds,BigDecimal serviceConsumableCost,String basis){}
 @Transactional(readOnly=true)public Report report(Instant from,Instant to){
  Instant end=to==null?Instant.now():to,start=from==null?end.minus(Duration.ofDays(30)):from;if(!start.isBefore(end))throw new BusinessException("From must precede to");Timestamp a=Timestamp.from(start),b=Timestamp.from(end);
  BigDecimal net=money("SELECT COALESCE(SUM(GREATEST(o.total_amount-o.shipping_fee-COALESCE((SELECT SUM(r.refund_amount) FROM product_returns r WHERE r.order_id=o.id AND r.status IN ('RECEIVED','REFUNDED')),0),0)),0) FROM orders o WHERE o.is_deleted=false AND o.appointment_id IS NULL AND o.service_package_id IS NULL AND o.status='DELIVERED' AND o.delivered_at>=? AND o.delivered_at<?",a,b);
  BigDecimal cost=money("SELECT COALESCE(SUM((s.quantity-s.returned_quantity)*s.unit_cost_snapshot),0) FROM stock_allocations s JOIN orders o ON o.order_number=s.order_number WHERE o.is_deleted=false AND o.status='DELIVERED' AND o.delivered_at>=? AND o.delivered_at<? AND s.status='DEDUCTED' AND s.unit_cost_snapshot IS NOT NULL",a,b);
  Long missing=jdbc.queryForObject("SELECT COUNT(*) FROM stock_allocations s JOIN orders o ON o.order_number=s.order_number WHERE o.is_deleted=false AND o.status='DELIVERED' AND o.delivered_at>=? AND o.delivered_at<? AND s.quantity>s.returned_quantity AND s.unit_cost_snapshot IS NULL",Long.class,a,b);
  Long legacy=jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE is_deleted=false AND status IN ('DELIVERED','RETURNED') AND appointment_id IS NULL AND service_package_id IS NULL AND delivered_at IS NULL",Long.class);
  return new Report(start,end,net,cost,missing,legacy,missing==0?net.subtract(cost):null,money("SELECT COALESCE(SUM(amount),0) FROM operating_expenses WHERE is_deleted=false AND incurred_at>=? AND incurred_at<?",a,b),money("SELECT COALESCE(SUM(amount),0) FROM procurement_movements WHERE kind='PAYMENT' AND created_at>=? AND created_at<?",a,b),money("SELECT COALESCE(SUM(amount),0) FROM procurement_movements WHERE kind='SUPPLIER_REFUND' AND created_at>=? AND created_at<?",a,b),money("SELECT COALESCE(SUM(amount),0) FROM payment_resolutions WHERE kind='REFUND' AND created_at>=? AND created_at<?",a,b),money("SELECT COALESCE(SUM(total_cost),0) FROM inventory_consumptions WHERE created_at>=? AND created_at<?",a,b),"Product margin is the current net contribution of the cohort delivered in [from,to); returns are reflected as of now. Shipping and Spa revenue are excluded. Expenses, supplier cash flows and service consumables use their own period timestamps and are separate; they are not a company net-profit calculation. Unknown historical costs are never replaced with current cost.");
 }
 @Transactional(readOnly=true)public Page<OperatingExpense> list(Pageable p){return expenses.findAll(p);}
 @Transactional public OperatingExpense record(Expense c){
  if(c==null||c.reference()==null||c.reference().isBlank()||c.reference().length()>100||c.category()==null||c.category().isBlank()||c.category().length()>50||c.description()==null||c.description().isBlank()||c.description().length()>500||c.amount()==null||c.amount().signum()<=0||c.amount().stripTrailingZeros().scale()>0||c.incurredAt()==null||c.incurredAt().isAfter(Instant.now().plusSeconds(60)))throw new BusinessException("Expense document, category, description, positive VND amount and incurred time required");
  var prior=expenses.findByReference(c.reference());if(prior.isPresent()){var r=prior.get();if(r.getAmount().compareTo(c.amount())!=0||!r.getCategory().equals(c.category())||!r.getDescription().equals(c.description())||!r.getIncurredAt().equals(c.incurredAt()))throw new BusinessException("Expense reference conflicts");return r;}
  var row=new OperatingExpense();row.setReference(c.reference());row.setCategory(c.category());row.setDescription(c.description());row.setAmount(c.amount());row.setIncurredAt(c.incurredAt());return expenses.saveAndFlush(row);
 }
 private BigDecimal money(String sql,Object...args){return jdbc.queryForObject(sql,BigDecimal.class,args);}
}

package com.core.beautyshop;

import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.dto.response.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.order.application.service.OrderRefundService;
import com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpaPackageCompletionIntegrationTest extends SpaWorkflowTestSupport {
    @Autowired SpaRefundService refunds;
    @Autowired SpaServiceRefundRepository refundRows;
    @Autowired SpaPurchaseSnapshotRepository snapshots;
    @Autowired SpaTicketService purchases;
    @Autowired PaidSpaTicketIssuer issuer;
    @Autowired AdminSpaCatalogService catalog;
    @Autowired SpaAppointmentCheckoutService checkout;

    void snapshot(Fixture f, int total, int used, String unitValue) {
        tx(() -> {
            var t=tickets.findById(f.ticketId()).orElseThrow();t.setTotalSessions(total);t.setUsedSessions(used);
            t.getEntitlements().put(f.serviceId(),new TicketEntitlement(total,used));
            var s=new SpaPurchaseSnapshot();s.setOrderId(t.getOrderId());s.setPackageId(t.getServicePackage().getId());
            s.setPackageName("Tên lúc mua");s.setPurchaseAmount(BigDecimal.valueOf(100));s.getEntitlements().put(f.serviceId(),total);
            s.getServiceDetails().put(f.serviceId(),new PurchasedServiceDetail("Dịch vụ lúc mua",new BigDecimal(unitValue)));
            snapshots.save(s);return null;
        });
    }
    SpaRefundService.Confirmation confirmation(String amount) {return new SpaRefundService.Confirmation(new BigDecimal(amount),UUID.randomUUID().toString(),"Khách ngừng sử dụng, đã đối soát");}
    @Test void partialPackageSettlementRevokesRightsAndRetryDoesNotRefundAgain() {
        var f=fixture();snapshot(f,4,1,"300");var c=confirmation("75");
        assertEquals(0,as(f.admin(),()->refunds.packageQuote(f.ticketId())).refundableAmount().compareTo(c.amount()));
        var first=as(f.admin(),()->refunds.settlePackage(f.ticketId(),c));
        var retry=as(f.admin(),()->refunds.settlePackage(f.ticketId(),c));assertEquals(first.getId(),retry.getId());
        tx(()->{var t=tickets.findById(f.ticketId()).orElseThrow();assertEquals(TicketStatus.REVOKED,t.getStatus());
            assertEquals(0,orders.findById(t.getOrderId()).orElseThrow().getRefundedAmount().compareTo(new BigDecimal("75")));return null;});
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.settlePackage(f.ticketId(),confirmation("75"))));
        assertThrows(BusinessException.class,()->as(f.customer(),()->appointments.bookAppointment(booking(f,f.firstStaffId(),LocalTime.of(10,0),f.ticketId()))));
    }
    @Test void reservedRightsAndLegacyPricesBlockSettlementWithoutChangingMoney() {
        var f=fixture();assertNotNull(as(f.admin(),()->refunds.packageQuote(f.ticketId())).blockedReason());
        snapshot(f,4,0,"100");
        as(f.customer(),()->appointments.bookAppointment(booking(f,f.firstStaffId(),LocalTime.of(10,0),f.ticketId())));
        assertTrue(as(f.admin(),()->refunds.packageQuote(f.ticketId())).blockedReason().contains("giữ lượt"));
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.settlePackage(f.ticketId(),confirmation("100"))));
        assertTrue(refundRows.findByTicketIdOrderByIdDesc(f.ticketId()).isEmpty());
    }
    @Test void compensationDoesNotIncreaseRefundAndFreeWeightsUseSessionCounts() {
        var f=fixture();snapshot(f,4,1,"0");tx(()->{var t=tickets.findById(f.ticketId()).orElseThrow();t.setTotalSessions(6);t.getEntitlements().get(f.serviceId()).setTotal(6);return null;});
        assertEquals(0,new BigDecimal("75").compareTo(as(f.admin(),()->refunds.packageQuote(f.ticketId())).refundableAmount()));
    }
    @Test void concurrentSettlementCannotPayTwice() throws Exception {
        var f=fixture();snapshot(f,4,1,"100");var c=confirmation("75");
        var attempts=concurrently(()->as(f.admin(),()->attempt(()->refunds.settlePackage(f.ticketId(),c))),
                ()->as(f.admin(),()->attempt(()->refunds.settlePackage(f.ticketId(),c))));
        assertTrue(attempts.stream().allMatch(Attempt::accepted));
        assertEquals(1,refundRows.findByTicketIdOrderByIdDesc(f.ticketId()).size());
        tx(()->{var t=tickets.findById(f.ticketId()).orElseThrow();assertEquals(0,new BigDecimal("75").compareTo(orders.findById(t.getOrderId()).orElseThrow().getRefundedAmount()));return null;});
    }
    @Test void mixedServiceSettlementUsesOriginalRelativeValuesAndRoundsDown() {
        var f=fixture();snapshot(f,2,1,"300");
        tx(()->{var s=services.save(BeautyService.builder().name("Dịch vụ nhẹ").slug(UUID.randomUUID().toString()).basePrice(new BigDecimal("100")).durationMinutes(30).build());
            var t=tickets.findById(f.ticketId()).orElseThrow();t.setTotalSessions(3);t.getEntitlements().put(s.getId(),new TicketEntitlement(1,0));
            var snapshot=snapshots.findById(t.getOrderId()).orElseThrow();snapshot.getEntitlements().put(s.getId(),1);snapshot.getServiceDetails().put(s.getId(),new PurchasedServiceDetail(s.getName(),s.getBasePrice()));
            services.findById(f.serviceId()).orElseThrow().setBasePrice(new BigDecimal("99999"));return null;});
        assertEquals(0,new BigDecimal("57").compareTo(as(f.admin(),()->refunds.packageQuote(f.ticketId())).refundableAmount()));
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.settlePackage(f.ticketId(),confirmation("58"))));
        assertTrue(refundRows.findByTicketIdOrderByIdDesc(f.ticketId()).isEmpty());
    }
    @Test void customerAndStaffCannotConfirmRefund() {
        var f=fixture();snapshot(f,4,1,"100");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->as(f.customer(),()->refunds.settlePackage(f.ticketId(),confirmation("75"))));
        var staff=tx(()->user("ROLE_STAFF"));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->as(staff,()->refunds.packageQuote(f.ticketId())));
    }
    @Test void usedRightsPreventTypeConversionAndUnsoldPackageReferenceAlsoPreventsIt() {
        var f=fixture();var service=tx(()->services.findById(f.serviceId()).orElseThrow());
        var cmd=new AdminSpaCatalogService.ServiceCommand(null,service.getName(),service.getSlug(),null,null,service.getBasePrice(),30,10,null,true,"TREATMENT_COURSE");
        assertThrows(BusinessException.class,()->catalog.saveService(service.getId(),cmd));
        tx(()->{var t=tickets.findById(f.ticketId()).orElseThrow();t.setStatus(TicketStatus.REVOKED);
            var pack=t.getServicePackage();pack.getItems().add(ServicePackageItem.builder().servicePackage(pack).service(services.getReferenceById(f.serviceId())).quantity(2).build());return null;});
        assertThrows(BusinessException.class,()->catalog.saveService(service.getId(),cmd));
    }
    @Test void invalidOldPackageWithTreatmentComponentCannotBePurchased() {
        var f=fixture();long packageId=tx(()->{var t=tickets.findById(f.ticketId()).orElseThrow();var p=t.getServicePackage();
            var s=services.findById(f.serviceId()).orElseThrow();s.setServiceType("TREATMENT_COURSE");p.getItems().add(ServicePackageItem.builder().servicePackage(p).service(s).quantity(2).build());return p.getId();});
        var request=new PurchasePackageRequest();request.setPackageId(packageId);
        assertThrows(BusinessException.class,()->as(f.customer(),()->purchases.purchasePackage(request)));
    }
    @Test void purchaseSnapshotSurvivesRenameAndTicketUsesOriginalNames() {
        var f=fixture();long packageId=tx(()->{var p=tickets.findById(f.ticketId()).orElseThrow().getServicePackage();p.setName("Gói gốc");
            p.getItems().add(ServicePackageItem.builder().servicePackage(p).service(services.getReferenceById(f.serviceId())).quantity(3).build());return p.getId();});
        var request=new PurchasePackageRequest();request.setPackageId(packageId);request.setIdempotencyKey(UUID.randomUUID().toString());
        var result=as(f.customer(),()->purchases.purchasePackage(request));
        tx(()->{var o=orders.findById(result.getOrderId()).orElseThrow();o.setPaymentStatus(PaymentStatus.PAID);o.setPaidAmount(o.getTotalAmount());o.setPaidAt(Instant.now());
            packages.findById(packageId).orElseThrow().setName("Tên mới");services.findById(f.serviceId()).orElseThrow().setName("Dịch vụ mới");return null;});
        issuer.issue(new com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent(result.getOrderId(),f.customer().getId(),packageId));
        tx(()->{var dto=UserServiceTicketResponse.of(tickets.findByOrderId(result.getOrderId()).orElseThrow());assertEquals("Gói gốc",dto.getPackageName());assertTrue(dto.isNameSnapshot());
            assertEquals("Concurrency service",dto.getServices().getFirst().serviceName());assertEquals(3,dto.getServices().getFirst().available());assertEquals("SERVICE_PACKAGE",dto.getBenefitType());return null;});
    }
    @Test void staleCatalogTokenPreventsBuyingDifferentRights() {
        var f=fixture();long packageId=tx(()->{var p=tickets.findById(f.ticketId()).orElseThrow().getServicePackage();p.getItems().add(ServicePackageItem.builder().servicePackage(p).service(services.getReferenceById(f.serviceId())).quantity(2).build());return p.getId();});
        String token=tx(()->ServicePackageResponse.token(packages.findById(packageId).orElseThrow()));
        tx(()->{packages.findById(packageId).orElseThrow().getItems().getFirst().setQuantity(5);return null;});
        var request=new PurchasePackageRequest();request.setPackageId(packageId);request.setPurchaseToken(token);
        assertThrows(BusinessException.class,()->as(f.customer(),()->purchases.purchasePackage(request)));
    }
    long visit(Fixture f) {
        return tx(()->{var a=Appointment.builder().userId(f.customer().getId()).appointmentDate(LocalDate.now().minusDays(1))
            .startTime(LocalTime.of(10,0)).endTime(LocalTime.of(11,0)).status(AppointmentStatus.COMPLETED).items(new ArrayList<>()).build();
            for(int price:new int[]{101,202})a.getItems().add(AppointmentItem.builder().appointment(a).service(services.getReferenceById(f.serviceId()))
                .serviceNameSnapshot("Dịch vụ hóa đơn "+price).price(BigDecimal.valueOf(price)).executionStatus(AppointmentItemExecutionStatus.PERFORMED)
                .startTime(LocalTime.of(10,0)).endTime(LocalTime.of(10,30)).build());
            return appointmentRows.saveAndFlush(a).getId();});
    }
    void payVisit(Fixture f,long aid) {
        as(f.admin(),()->checkout.create(aid,new CreateSpaVisitInvoiceRequest(PaymentMethod.CASH,""),UUID.randomUUID().toString()));
        tx(()->{var o=orders.findById(appointmentRows.findById(aid).orElseThrow().getOrderId()).orElseThrow();o.setPaidAmount(o.getTotalAmount());o.setPaymentStatus(PaymentStatus.PAID);o.setPaidAt(Instant.now());return null;});
    }
    @Test void partialVisitRefundIsBoundedByLineAndReplayIsIdempotent() {
        var f=fixture();long aid=visit(f);payVisit(f,aid);var q=as(f.admin(),()->refunds.visitQuote(aid));long item=q.items().getFirst().appointmentItemId();
        var c=confirmation("50");var first=as(f.admin(),()->refunds.refundVisitLine(aid,item,c));assertEquals(first.getId(),as(f.admin(),()->refunds.refundVisitLine(aid,item,c)).getId());
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.refundVisitLine(aid,item,confirmation("52"))));
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.refundVisitLine(aid,item+999999,confirmation("1"))));
        var after=as(f.admin(),()->refunds.visitQuote(aid));assertEquals(0,new BigDecimal("51").compareTo(after.items().getFirst().refundableAmount()));
        assertEquals(0,new BigDecimal("253").compareTo(after.availableReceivedAmount()));
        assertThrows(BusinessException.class,()->as(f.admin(),()->refunds.refundVisitLine(aid,item,new SpaRefundService.Confirmation(new BigDecimal("49"),c.reference(),c.reason()))));
    }
    @Test void membershipDiscountAllocationSumsExactlyToInvoiceTotal() {
        var f=fixture();long aid=visit(f);payVisit(f,aid);
        tx(()->{var o=orders.findById(appointmentRows.findById(aid).orElseThrow().getOrderId()).orElseThrow();o.setTotalAmount(new BigDecimal("272"));o.setPaidAmount(new BigDecimal("272"));return null;});
        var q=as(f.admin(),()->refunds.visitQuote(aid));assertEquals(0,new BigDecimal("272").compareTo(q.items().stream().map(SpaRefundService.LineQuote::allocatedAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertEquals(0,new BigDecimal("90").compareTo(q.items().getFirst().allocatedAmount()));
    }
}

package com.core.beautyshop.modules.crm.application;

import com.core.beautyshop.modules.crm.domain.CustomerCareNoteRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CustomerCarePaginationTest {
    private CustomerCareService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:crm_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE users(id BIGINT,username VARCHAR(100),full_name VARCHAR(100),email VARCHAR(100),phone VARCHAR(20),membership_tier VARCHAR(30),loyalty_points INT,status VARCHAR(20),is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE orders(id BIGINT,user_id BIGINT,paid_amount DECIMAL(14,2),refunded_amount DECIMAL(14,2) DEFAULT 0,payment_status VARCHAR(30),is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE appointments(id BIGINT,user_id BIGINT,is_deleted BOOLEAN)");
        for (int index = 1; index <= 105; index++) {
            jdbc.update("INSERT INTO users VALUES (?,?,?,?,?,'BRONZE',0,'ACTIVE',false)", index, "customer" + index, "Customer " + index, "customer" + index + "@example.test", "0900000000");
        }
        jdbc.update("UPDATE users SET username='literal%name' WHERE id=1");
        jdbc.update("INSERT INTO users VALUES (999,'removed','Removed','removed@example.test','000','BRONZE',0,'ACTIVE',true)");
        service = new CustomerCareService(jdbc, mock(CustomerCareNoteRepository.class), mock(com.core.beautyshop.modules.identity.api.IdentityFacade.class));
    }

    @Test
    void allCustomersRemainReachableBeyondLegacy100LimitWithStablePagesAndAccurateTotals() {
        var page = service.searchPage(null, 5, 20);
        assertThat(page.getTotalElements()).isEqualTo(105);
        assertThat(page.getTotalPages()).isEqualTo(6);
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.isLast()).isTrue();
        assertThat(service.searchPage(null, 0, 20).getContent().get(0).id()).isEqualTo(105L);
        assertThat(service.search(null)).hasSize(100);
        assertThat(service.searchPage("%", 0, 20).getContent()).extracting(CustomerCareService.CustomerView::id).containsExactly(1L);
    }

    @Test
    void lifetimeValueUsesNetReceiptsIncludingPartialPaymentsAndClampsOverRefundedRowsAtZero() {
        jdbc.update("INSERT INTO orders(id,user_id,paid_amount,refunded_amount,payment_status,is_deleted) VALUES (1,1,100000,40000,'PAID',false)");
        jdbc.update("INSERT INTO orders(id,user_id,paid_amount,refunded_amount,payment_status,is_deleted) VALUES (2,2,50000,0,'PENDING',false)");
        jdbc.update("INSERT INTO orders(id,user_id,paid_amount,refunded_amount,payment_status,is_deleted) VALUES (3,3,0,1000,'REFUNDED',false)");
        jdbc.update("INSERT INTO orders(id,user_id,paid_amount,refunded_amount,payment_status,is_deleted) VALUES (4,1,900000,0,'PAID',true)");
        assertThat(service.getCustomer(1L).lifetimeValue()).isEqualByComparingTo("60000");
        assertThat(service.getCustomer(2L).lifetimeValue()).isEqualByComparingTo("50000");
        assertThat(service.getCustomer(3L).lifetimeValue()).isEqualByComparingTo("0");
        assertThat(service.searchPage(null, 0, 2).getContent()).extracting(CustomerCareService.CustomerView::id).containsExactly(1L, 2L);
    }

    @Test
    void invalidAndOverflowingPaginationAndOverlongKeywordAreRejectedBeforeQuerying() {
        assertThatThrownBy(() -> service.searchPage(null, -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.searchPage(null, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.searchPage(null, 0, 101)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.searchPage(null, Integer.MAX_VALUE, 100)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.searchPage("x".repeat(101), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.notesPage(1L, -1, 20)).isInstanceOf(BusinessException.class);
    }
}

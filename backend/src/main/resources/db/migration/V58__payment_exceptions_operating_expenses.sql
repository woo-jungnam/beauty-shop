CREATE TABLE payment_resolutions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,transaction_id BIGINT NOT NULL UNIQUE,kind VARCHAR(20) NOT NULL,order_number VARCHAR(50),reference VARCHAR(100) UNIQUE,reason VARCHAR(500) NOT NULL,amount DECIMAL(14,2) NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE operating_expenses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,reference VARCHAR(100) NOT NULL UNIQUE,category VARCHAR(50) NOT NULL,description VARCHAR(500) NOT NULL,amount DECIMAL(14,2) NOT NULL,incurred_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE recall_outreach (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,recall_id BIGINT NOT NULL,subject_type VARCHAR(20) NOT NULL,subject_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,outcome VARCHAR(1000),actor_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE KEY uk_recall_outreach_subject(recall_id,subject_type,subject_id)
);
CREATE TABLE recall_outreach_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,recall_id BIGINT NOT NULL,subject_type VARCHAR(20) NOT NULL,subject_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,outcome VARCHAR(1000) NOT NULL,actor_id BIGINT NOT NULL,created_at DATETIME(6) NOT NULL,
 INDEX ix_recall_outreach_event(recall_id,id)
);

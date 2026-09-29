package com.core.beautyshop.shared.maintenance;

import com.core.beautyshop.modules.identity.application.service.AuthService;
import com.core.beautyshop.modules.notification.application.service.NotificationService;
import com.core.beautyshop.shared.outbox.application.service.OutboxClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionJob {

    private final OutboxClaimService outboxClaimService;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Value("${app.retention.days:14}")
    private int retentionDays = 14;

    @Scheduled(cron = "${app.retention.cron:0 0 3 * * SUN}", zone = "${app.time-zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = "DataRetentionJob_run", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void cleanupOldData() {
        log.info("Bắt đầu Scheduled Job dọn dẹp dữ liệu cũ (retention = {} ngày)...", retentionDays);
        try {
            int outboxCount = outboxClaimService.cleanupPublishedMessages(retentionDays);
            int sessionCount = authService.cleanupExpiredSessions(retentionDays);
            int receiptCount = notificationService.cleanupOldReceipts(retentionDays);
            log.info("Hoàn tất dọn dẹp dữ liệu lưu trữ: {} outbox messages, {} refresh sessions, {} notification receipts.",
                    outboxCount, sessionCount, receiptCount);
        } catch (Exception ex) {
            log.error("Lỗi trong quá trình thực thi DataRetentionJob: {}", ex.getMessage(), ex);
        }
    }
}

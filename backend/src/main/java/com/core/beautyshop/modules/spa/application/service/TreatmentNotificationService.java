package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.treatment.TreatmentNotification;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentNotificationRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Customer notifications deliberately contain no clinical notes or internal audit details. */
@Service @RequiredArgsConstructor
public class TreatmentNotificationService {
    private final TreatmentNotificationRepository notifications;
    @org.springframework.beans.factory.annotation.Autowired
    private com.core.beautyshop.modules.notification.api.CustomerNotificationFacade customerInbox;
    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("PURCHASE_PAID", "Lộ trình đã thanh toán"),
            Map.entry("ENROLLED", "Lộ trình đã được cấp"),
            Map.entry("COURSE_CREATED", "Lộ trình đã được cấp"),
            Map.entry("INTAKE", "Đã tiếp nhận lộ trình"),
            Map.entry("INTAKE_COMPLETED", "Đã tiếp nhận lộ trình"),
            Map.entry("INTAKE_ACCEPTED", "Đã tiếp nhận lộ trình"),
            Map.entry("CHANGE_PROPOSED", "Có đề xuất điều chỉnh lộ trình"),
            Map.entry("CHANGE_APPROVED", "Điều chỉnh đang chờ bạn xác nhận"),
            Map.entry("CHANGE_APPLIED", "Đã áp dụng điều chỉnh lộ trình"),
            Map.entry("SESSION_BOOKED", "Đã ghi nhận lịch điều trị"),
            Map.entry("SESSION_COMPLETED", "Đã cập nhật kết quả buổi điều trị"),
            Map.entry("SESSION_RESULT", "Đã cập nhật kết quả buổi điều trị"),
            Map.entry("PAUSED", "Lộ trình đã tạm dừng"),
            Map.entry("RESUMED", "Lộ trình đã tiếp tục"),
            Map.entry("EXTENDED", "Đã gia hạn lộ trình"),
            Map.entry("CANCELLED", "Lộ trình đã dừng"),
            Map.entry("REFUND_CONFIRMED", "Đã xác nhận hoàn tiền"),
            Map.entry("REQUEST_APPROVED", "Yêu cầu về lộ trình đã được duyệt"),
            Map.entry("REQUEST_REJECTED", "Đã có phản hồi cho yêu cầu về lộ trình"),
            Map.entry("SETTLEMENT_REFUND_PENDING", "Khoản thanh toán chênh lệch đang chờ hoàn"),
            Map.entry("COMPLETED", "Lộ trình đã hoàn tất"),
            Map.entry("COURSE_COMPLETED", "Lộ trình đã hoàn tất"));

    @Transactional
    public void record(Long courseId, Long userId, String action, Long auditId) {
        String title = TITLES.get(action);
        if (title != null && courseId != null && userId != null && auditId != null)
            enqueue(courseId, userId, "AUDIT:" + auditId, action, title,
                    "Mở hồ sơ lộ trình để xem thông tin và các bước tiếp theo.");
    }

    @Transactional
    public void enqueue(Long courseId, Long userId, String eventKey, String kind, String title, String message) {
        if (notifications.existsByEventKey(eventKey)) return;
        var row = new TreatmentNotification();
        row.setCourseId(courseId); row.setUserId(userId); row.setEventKey(eventKey);
        row.setKind(kind); row.setTitle(title); row.setMessage(message);
        notifications.save(row);
        if(customerInbox!=null)customerInbox.publish(userId,"treatment:"+eventKey,"TREATMENT",title,message,"treatments?course="+courseId);
    }

    @Transactional(readOnly = true)
    public List<View> mine() {
        return notifications.findTop100ByUserIdOrderByIdDesc(SecurityUtils.getCurrentUserId()).stream().map(View::of).toList();
    }

    @Transactional
    public View read(Long id) {
        var row = notifications.findById(id).filter(n -> n.getUserId().equals(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        if (row.getReadAt() == null) row.setReadAt(Instant.now());
        return View.of(row);
    }

    public record View(Long id, Long courseId, String kind, String title, String message, Instant createdAt, Instant readAt) {
        static View of(TreatmentNotification n) {
            return new View(n.getId(), n.getCourseId(), n.getKind(), n.getTitle(), n.getMessage(), n.getCreatedAt(), n.getReadAt());
        }
    }
}

package com.core.beautyshop.modules.spa.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(description = "Dịch vụ trong buổi, planned interval tách khỏi actual execution và trạng thái sử dụng vé")
public class AppointmentItemResponse {
    private Long id;
    private Long serviceId;
    private Long staffId;
    @Schema(description = "Tên đã chụp khi book; legacy có thể lấy tên catalog hiện tại")
    private String serviceName;
    @Schema(description = "Nhãn staff hiện tại, ví dụ Staff #1; không bảo đảm là fullName tài khoản")
    private String staffName;
    private Long ticketId;
    @Schema(description = "true nghĩa item có gắn ticketId, kể cả còn RESERVED; không chứng minh đã REDEEMED hoặc đã phục vụ")
    private Boolean isTicketUsed;
    @Schema(description = "Giá VND lưu lúc book; item dùng vé là 0; invoice chỉ lấy PERFORMED không dùng vé")
    private BigDecimal price;
    @Schema(description = "Giờ dự kiến tại Việt Nam", type = "string", format = "time", example = "10:00:00")
    private LocalTime startTime;
    @Schema(description = "Giờ dự kiến kết thúc gồm thời gian chuẩn bị", type = "string", format = "time", example = "11:00:00")
    private LocalTime endTime;
    @Schema(description = "Kết quả execution; LEGACY_FINALIZED thiếu bằng chứng thực tế, NO_SHOW không phải PERFORMED")
    private com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus executionStatus;
    @Schema(description = "NONE/RESERVED/REDEEMED/RELEASED/FORFEITED/LEGACY_FINALIZED; phân biệt giữ, dùng và mất lượt no-show")
    private com.core.beautyshop.modules.spa.domain.enums.TicketUsageState ticketUsageState;
    private java.time.Instant actualStartedAt;
    private java.time.Instant actualCompletedAt;
    @Schema(description = "Actor đã bắt đầu item, là User.id; null với legacy hoặc item chưa START")
    private Long performedByUserId;
    private String executionNotes;
    @Schema(description = "Facility đầu tiên/field legacy; dùng resources để xem đầy đủ bed+machine")
    private Long facilityId;
    @Schema(description = "Allocations đã lưu; legacy chỉ có facilityId có thể trả array rỗng")
    private java.util.List<ResourceAllocation> resources;
    @Schema(name = "SpaItemResourceAllocation", description = "Một tài nguyên và số đơn vị được phân bổ cho item")
    public record ResourceAllocation(Long facilityId, String name, String type, @Schema(minimum = "1") int quantity) { }

}

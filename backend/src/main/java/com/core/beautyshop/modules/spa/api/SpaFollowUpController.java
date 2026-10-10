package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaNotificationWorkflow;
import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spa/appointments/{id}/follow-up-instructions")
@Tag(name = "Hướng dẫn sau buổi Spa", description = "Nội dung do nhân viên viết; ghi hướng dẫn không đồng nghĩa email đã được gửi")
public class SpaFollowUpController {
    private final SpaNotificationWorkflow workflow;

    @Operation(summary = "Xem hướng dẫn sau buổi đã ghi", description = "Chủ lịch, ADMIN/STAFF/SPA_RECEPTION hoặc SPA_THERAPIST được giao lịch. Danh sách theo ID giảm dần, không phân trang; không trả private care notes.")
    @GetMapping
    public ApiResponse<List<SpaNotificationWorkflow.Instruction>> instructions(@PathVariable Long id) {
        return ApiResponse.success(workflow.instructions(id));
    }

    @Operation(summary = "Nhân viên ghi hướng dẫn cho item đã thực hiện", description = "ADMIN/STAFF hoặc SPA_THERAPIST được giao đúng item PERFORMED/LEGACY_FINALIZED. content 1–4000 ký tự, dueAt là Instant từ hiện tại trừ dung sai một phút đến một năm. Lưu append-only; gửi tự động mặc định tắt, chỉ enqueue sau khi cả lịch COMPLETED và đến dueAt. Không sinh nội dung chuyên môn tự động, chưa có API sửa/thu hồi instruction hoặc replay email.")
    @PostMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.CREATED)
    @AuditAction(action="WRITE_SPA_FOLLOW_UP", resourceType="APPOINTMENT")
    public ApiResponse<SpaNotificationWorkflow.Instruction> add(@PathVariable Long id, @PathVariable Long itemId,
            @RequestBody SpaNotificationWorkflow.InstructionCommand command) {
        return ApiResponse.created(workflow.addInstruction(id, itemId, command), "Staff follow-up instruction recorded");
    }
}

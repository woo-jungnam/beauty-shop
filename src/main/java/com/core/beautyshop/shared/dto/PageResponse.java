package com.core.beautyshop.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Đối tượng đóng gói dữ liệu phân trang")
public class PageResponse<T> {

    @Schema(description = "Danh sách phần tử của trang hiện tại")
    private List<T> content;

    @Schema(description = "Số thứ tự trang hiện tại (bắt đầu từ 0)", example = "0")
    private int page;

    @Schema(description = "Số lượng phần tử tối đa trên mỗi trang", example = "20")
    private int size;

    @Schema(description = "Tổng số phần tử trên toàn hệ thống", example = "100")
    private long totalElements;

    @Schema(description = "Tổng số trang", example = "5")
    private int totalPages;

    @Schema(description = "Có phải trang cuối cùng hay không", example = "false")
    private boolean last;

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}

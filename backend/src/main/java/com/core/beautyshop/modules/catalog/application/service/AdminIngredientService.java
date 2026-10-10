package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminIngredientService {
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final ProductIngredientRepository mappingRepository;

    @Transactional(readOnly = true)
    public Page<IngredientView> list(Pageable pageable) { return ingredientRepository.findByIsDeletedFalse(pageable).map(IngredientView::from); }
    @Transactional(readOnly = true)
    public IngredientView get(Long id) {
        return ingredientRepository.findByIdAndIsDeletedFalse(id)
                .map(IngredientView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + id));
    }
    @Transactional
    public IngredientView save(Long id, IngredientCommand command) {
        if (command.name() == null || command.name().isBlank() || command.inciName() == null || command.inciName().isBlank()
                || command.slug() == null || command.slug().isBlank()) throw new BusinessException("Name, INCI name and slug are required");
        if (command.ewgScore() != null && (command.ewgScore() < 1 || command.ewgScore() > 10)) throw new BusinessException("EWG score must be from 1 to 10");
        Ingredient entity = id == null ? new Ingredient() : ingredientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + id));
        ingredientRepository.findBySlugAndIsDeletedFalse(command.slug()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("Ingredient slug already exists"); });
        entity.setName(command.name().trim()); entity.setInciName(command.inciName().trim()); entity.setSlug(command.slug().trim());
        entity.setDescription(command.description()); entity.setFunctions(copy(command.functions())); entity.setBenefits(copy(command.benefits()));
        entity.setPotentialConcerns(copy(command.potentialConcerns())); entity.setEwgScore(command.ewgScore() == null ? 1 : command.ewgScore());
        entity.setIsActiveIngredient(Boolean.TRUE.equals(command.activeIngredient()));
        return IngredientView.from(ingredientRepository.save(entity));
    }
    @Transactional
    public void delete(Long id) { Ingredient entity = ingredientRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found")); entity.setIsDeleted(true); }
    @Transactional(readOnly = true)
    public List<ProductIngredientView> productIngredients(Long productId) {
        return mappingRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream().map(ProductIngredientView::from).toList();
    }
    @Transactional
    public List<ProductIngredientView> replaceProductIngredients(Long productId, List<ProductIngredientCommand> commands) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Set<Long> ids = new HashSet<>();
        List<ProductIngredient> mappings = new ArrayList<>();
        int order = 0;
        for (ProductIngredientCommand command : commands == null ? List.<ProductIngredientCommand>of() : commands) {
            if (!ids.add(command.ingredientId())) throw new BusinessException("Duplicate ingredient mapping");
            Ingredient ingredient = ingredientRepository.findByIdAndIsDeletedFalse(command.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + command.ingredientId()));
            if (command.concentration() != null && command.concentration().signum() < 0) throw new BusinessException("Concentration cannot be negative");
            mappings.add(ProductIngredient.builder().product(product).ingredient(ingredient).concentration(command.concentration())
                    .concentrationUnit(command.concentrationUnit() == null ? "%" : command.concentrationUnit())
                    .isKeyActive(Boolean.TRUE.equals(command.keyActive())).displayOrder(command.displayOrder() == null ? order++ : command.displayOrder()).build());
        }
        mappingRepository.deleteAll(mappingRepository.findByProductIdOrderByDisplayOrderAsc(productId));
        mappingRepository.flush();
        return mappingRepository.saveAll(mappings).stream().map(ProductIngredientView::from).toList();
    }
    private List<String> copy(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }

    @Schema(name = "IngredientWriteRequest", description = "Tạo/cập nhật toàn bộ hoạt chất; field tùy chọn null được thay bằng giá trị mặc định, không phải cập nhật một phần")
    public record IngredientCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Tên hoạt chất không rỗng") String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Tên INCI không rỗng", example = "Niacinamide") String inciName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Slug duy nhất trong hoạt chất chưa xóa") String slug,
            @Schema(description = "Mô tả") String description, @Schema(description = "Chức năng; null thành []") List<String> functions,
            @Schema(description = "Lợi ích; null thành []") List<String> benefits,
            @Schema(description = "Lưu ý; null thành []") List<String> potentialConcerns,
            @Schema(description = "Điểm 1–10; bỏ qua/null dùng 1", minimum = "1", maximum = "10", example = "1") Integer ewgScore,
            @Schema(description = "Hoạt chất chính; null thành false") Boolean activeIngredient) { }
    @Schema(name = "IngredientView", description = "Thông tin hoạt chất")
    public record IngredientView(Long id, String name, String inciName, String slug, String description, List<String> functions,
            List<String> benefits, List<String> potentialConcerns, Integer ewgScore, Boolean activeIngredient) {
        static IngredientView from(Ingredient value) { return new IngredientView(value.getId(), value.getName(), value.getInciName(), value.getSlug(), value.getDescription(),
                value.getFunctions(), value.getBenefits(), value.getPotentialConcerns(), value.getEwgScore(), value.getIsActiveIngredient()); }
    }
    @Schema(name = "ProductIngredientWriteRequest", description = "Một phần tử trong mảng thay thế toàn bộ mapping thành phần")
    public record ProductIngredientCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID hoạt chất chưa xóa") Long ingredientId,
            @Schema(description = "Nồng độ không âm, tùy chọn", minimum = "0") BigDecimal concentration,
            @Schema(description = "Đơn vị; null dùng %", example = "%") String concentrationUnit,
            @Schema(description = "Hoạt chất nổi bật; null dùng false") Boolean keyActive,
            @Schema(description = "Thứ tự hiển thị; null tự cấp theo vị trí") Integer displayOrder) { }
    @Schema(name = "ProductIngredientView", description = "Mapping thành phần của sản phẩm")
    public record ProductIngredientView(Long id, Long ingredientId, String ingredientName, BigDecimal concentration, String concentrationUnit, Boolean keyActive, Integer displayOrder) {
        static ProductIngredientView from(ProductIngredient value) { return new ProductIngredientView(value.getId(), value.getIngredient().getId(), value.getIngredient().getName(),
                value.getConcentration(), value.getConcentrationUnit(), value.getIsKeyActive(), value.getDisplayOrder()); }
    }
}

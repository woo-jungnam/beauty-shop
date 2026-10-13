package com.core.beautyshop.shared.config;

import com.core.beautyshop.shared.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RecommendationSettings {
    private RecommendationSettings() {}
    public record Spec(Object defaultValue, double min, double max, String type) {}
    public static final Map<String, Spec> SPECS = Map.ofEntries(
        Map.entry("weight.view", new Spec(1.0, 0, 20, "DECIMAL")),
        Map.entry("weight.search_click", new Spec(1.5, 0, 20, "DECIMAL")),
        Map.entry("weight.add_to_cart", new Spec(3.0, 0, 20, "DECIMAL")),
        Map.entry("weight.purchase", new Spec(5.0, 0, 20, "DECIMAL")),
        Map.entry("history_window", new Spec(100, 1, 1000, "INTEGER")),
        Map.entry("half_life_days", new Spec(30.0, 0, 3650, "DECIMAL")),
        Map.entry("max_product_weight", new Spec(10.0, 0.1, 100, "DECIMAL")),
        Map.entry("min_similarity", new Spec(0.0, 0, 1, "DECIMAL")),
        Map.entry("catalog_refresh_seconds", new Spec(30, 5, 3600, "INTEGER")),
        Map.entry("exclude_seen", new Spec(true, 0, 0, "BOOLEAN")),
        Map.entry("in_stock_only", new Spec(true, 0, 0, "BOOLEAN")),
        Map.entry("field.name", new Spec(1.0, 0, 10, "DECIMAL")),
        Map.entry("field.category", new Spec(2.0, 0, 10, "DECIMAL")),
        Map.entry("field.brand", new Spec(0.5, 0, 10, "DECIMAL")),
        Map.entry("field.description", new Spec(1.0, 0, 10, "DECIMAL")),
        Map.entry("field.ingredients", new Spec(3.0, 0, 10, "DECIMAL")),
        Map.entry("field.skin", new Spec(2.0, 0, 10, "DECIMAL")),
        Map.entry("field.concerns", new Spec(2.0, 0, 10, "DECIMAL")),
        Map.entry("field.formula", new Spec(0.5, 0, 10, "DECIMAL")));

    public static Object value(String key, Object raw) {
        Spec spec = SPECS.get(key);
        if (spec == null) throw new BusinessException("Khóa cấu hình gợi ý không được hỗ trợ: " + key);
        try {
            String text = String.valueOf(raw);
            if ("BOOLEAN".equals(spec.type())) {
                if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException();
                return Boolean.parseBoolean(text);
            }
            double number = Double.parseDouble(text);
            if (!Double.isFinite(number) || number < spec.min() || number > spec.max()) throw new IllegalArgumentException();
            if ("INTEGER".equals(spec.type())) {
                if (number != Math.rint(number)) throw new IllegalArgumentException();
                return (int) number;
            }
            return number;
        } catch (RuntimeException error) {
            throw new BusinessException("Giá trị cấu hình " + key + " chưa hợp lệ hoặc nằm ngoài phạm vi cho phép");
        }
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        SPECS.forEach((key, spec) -> values.put(key, spec.defaultValue()));
        return values;
    }

    public static void requireSignal(Map<String, Object> values) {
        boolean positive = values.entrySet().stream().filter(e -> e.getKey().startsWith("weight."))
            .anyMatch(e -> ((Number) e.getValue()).doubleValue() > 0);
        if (!positive) throw new BusinessException("Cần ít nhất một trọng số hành vi lớn hơn 0");
        if (values.entrySet().stream().filter(e -> e.getKey().startsWith("field."))
                .noneMatch(e -> ((Number)e.getValue()).doubleValue() > 0))
            throw new BusinessException("Cần ít nhất một nhóm thuộc tính có trọng số lớn hơn 0");
    }
}

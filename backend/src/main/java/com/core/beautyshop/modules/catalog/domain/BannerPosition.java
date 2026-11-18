package com.core.beautyshop.modules.catalog.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Các vị trí hiển thị hợp lệ của Banner trong hệ thống.
 */
public final class BannerPosition {

    private BannerPosition() {}

    public static final String HERO_SLIDE = "HERO_SLIDE";
    public static final String HERO_SIDE = "HERO_SIDE";
    public static final String SECTION_FLASH_SALE = "SECTION_FLASH_SALE";
    public static final String SECTION_CATEGORIES = "SECTION_CATEGORIES";
    public static final String SECTION_BRANDS = "SECTION_BRANDS";
    public static final String SECTION_RECOMMENDATIONS = "SECTION_RECOMMENDATIONS";
    public static final String SECTION_CATALOG = "SECTION_CATALOG";
    public static final String SECTION_SPA = "SECTION_SPA";
    public static final String TREATMENT_HERO = "TREATMENT_HERO";
    public static final String CATALOG_HERO = "CATALOG_HERO";
    public static final String BOOKING_HERO = "BOOKING_HERO";
    public static final String PROMO = "PROMO";

    private static final Map<String, String> POSITION_LABELS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(HERO_SLIDE, "Slide chính trang chủ (Hero Slider)");
        map.put(HERO_SIDE, "Banner phụ bên cạnh Hero Slider");
        map.put(SECTION_FLASH_SALE, "Banner dài đầu mục Flash Sale Giờ Vàng");
        map.put(SECTION_CATEGORIES, "Banner dài đầu mục Danh Mục Sản Phẩm");
        map.put(SECTION_BRANDS, "Banner dài đầu mục Thương Hiệu Đối Tác");
        map.put(SECTION_RECOMMENDATIONS, "Banner dài đầu mục Gợi Ý Cho Bạn");
        map.put(SECTION_CATALOG, "Banner dài đầu mục Tất Cả Sản Phẩm");
        map.put(SECTION_SPA, "Banner dài đầu mục Dịch Vụ Spa Làm Đẹp");
        map.put(TREATMENT_HERO, "Ảnh đầu trang Lộ trình chăm sóc");
        map.put(CATALOG_HERO, "Ảnh đầu trang Danh sách sản phẩm");
        map.put(BOOKING_HERO, "Ảnh đầu trang Đặt lịch Spa");
        map.put(PROMO, "Khuyến mãi khác / Promo");
        POSITION_LABELS = Collections.unmodifiableMap(map);
    }

    public static Set<String> getAllowedPositions() {
        return POSITION_LABELS.keySet();
    }

    public static boolean isValid(String position) {
        if (position == null || position.isBlank()) return false;
        return POSITION_LABELS.containsKey(position.trim().toUpperCase());
    }

    public static String getLabel(String position) {
        if (position == null) return "Mặc định";
        return POSITION_LABELS.getOrDefault(position.trim().toUpperCase(), position);
    }
}


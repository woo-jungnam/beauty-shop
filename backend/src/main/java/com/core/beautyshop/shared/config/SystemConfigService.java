package com.core.beautyshop.shared.config;

import com.core.beautyshop.shared.config.domain.SystemConfig;
import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemConfigService {
    private static final Map<String, String> KNOWN_TYPES = Map.ofEntries(
            Map.entry("inventory.expiry_warning_days", "INTEGER"), Map.entry("dashboard.monthly_order_target", "INTEGER"),
            Map.entry("dashboard.monthly_revenue_target", "DECIMAL"),
            Map.entry("shipping.free_threshold", "DECIMAL"), Map.entry("shipping.default_fee", "DECIMAL"),
            Map.entry("spa.opening_time", "STRING"), Map.entry("spa.closing_time", "STRING"),
            Map.entry("spa.booking.pending_ttl_minutes", "INTEGER"), Map.entry("spa.booking.cancel_cutoff_minutes", "INTEGER"),
            Map.entry("spa.booking.reschedule_cutoff_minutes", "INTEGER"), Map.entry("spa.booking.no_show_grace_minutes", "INTEGER"),
            Map.entry("spa.booking.no_show_quota_action", "STRING"), Map.entry("spa.ticket.expiry_check_mode", "STRING"),
            Map.entry("spa.booking.policy_version", "STRING"));
    private static final Set<String> VALUE_TYPES = Set.of("STRING", "INTEGER", "DECIMAL", "BOOLEAN", "JSON");
    private final SystemConfigRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SystemConfig create(String key, String value, String description, String valueType) {
        if (key == null || key.isBlank() || key.trim().length() > 100) throw new BusinessException("Config key is required and must not exceed 100 characters");
        if (description != null && description.length() > 500) throw new BusinessException("Config description must not exceed 500 characters");
        String normalizedKey = key.trim();
        SystemConfig config = repository.findByConfigKeyForUpdate(normalizedKey).orElse(null);
        if (config != null && !Boolean.TRUE.equals(config.getIsDeleted())) throw new BusinessException("Config key already exists: " + normalizedKey);
        String type = normalizeType(normalizedKey, valueType == null && config != null ? config.getValueType() : valueType);
        String normalizedValue = validate(normalizedKey, value, type);
        if (config == null) config = SystemConfig.builder().configKey(normalizedKey).build();
        config.setIsDeleted(false);
        config.setConfigValue(normalizedValue);
        config.setValueType(type);
        config.setDescription(description);
        return repository.save(config);
    }

    @Transactional
    public SystemConfig update(String key, String value) {
        SystemConfig config = activeForUpdate(key);
        config.setConfigValue(validate(key, value, normalizeType(key, config.getValueType())));
        return repository.save(config);
    }

    @Transactional
    public void delete(String key) {
        SystemConfig config = activeForUpdate(key);
        config.setIsDeleted(true);
        repository.save(config);
    }

    @Transactional(readOnly = true)
    public int expiryWarningDays() {
        return repository.findByConfigKeyAndIsDeletedFalse("inventory.expiry_warning_days")
                .map(config -> Integer.parseInt(validate(config.getConfigKey(), config.getConfigValue(), "INTEGER")))
                .orElse(30);
    }

    @Transactional(readOnly = true)
    public BigDecimal getShippingFreeThreshold() {
        return repository.findByConfigKeyAndIsDeletedFalse("shipping.free_threshold")
                .map(config -> new BigDecimal(config.getConfigValue()))
                .orElse(new BigDecimal("249000"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getDefaultShippingFee() {
        return repository.findByConfigKeyAndIsDeletedFalse("shipping.default_fee")
                .map(config -> new BigDecimal(config.getConfigValue()))
                .orElse(new BigDecimal("25000"));
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateShippingFee(BigDecimal subTotal) {
        if (subTotal == null || subTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal threshold = getShippingFreeThreshold();
        if (subTotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return getDefaultShippingFee();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicConfigs() {
        var list = repository.findByIsPublicTrueAndIsDeletedFalse();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (var c : list) {
            Object val = c.getConfigValue();
            if ("DECIMAL".equalsIgnoreCase(c.getValueType())) {
                try { val = new BigDecimal(c.getConfigValue()); } catch (Exception ignored) {}
            } else if ("INTEGER".equalsIgnoreCase(c.getValueType())) {
                try { val = Long.parseLong(c.getConfigValue()); } catch (Exception ignored) {}
            } else if ("BOOLEAN".equalsIgnoreCase(c.getValueType())) {
                val = Boolean.parseBoolean(c.getConfigValue());
            }
            result.put(c.getConfigKey(), val);
        }
        if (!result.containsKey("shipping.free_threshold")) {
            result.put("shipping.free_threshold", new BigDecimal("249000"));
        }
        if (!result.containsKey("shipping.default_fee")) {
            result.put("shipping.default_fee", new BigDecimal("25000"));
        }
        return result;
    }

    private SystemConfig activeForUpdate(String key) {
        return repository.findByConfigKeyForUpdate(key).filter(config -> !Boolean.TRUE.equals(config.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("System config not found: " + key));
    }

    private String normalizeType(String key, String suppliedType) {
        String known = KNOWN_TYPES.get(key);
        String type = suppliedType == null || suppliedType.isBlank() ? (known == null ? "STRING" : known) : suppliedType.toUpperCase(Locale.ROOT);
        if (!VALUE_TYPES.contains(type)) throw new BusinessException("Unsupported config value type");
        if (known != null && !known.equals(type)) throw new BusinessException("Config requires value type " + known);
        return type;
    }

    private String validate(String key, String rawValue, String type) {
        if (rawValue == null || rawValue.isBlank()) throw new BusinessException("Config value is required");
        String value = rawValue.trim();
        try {
            switch (type) {
                case "INTEGER" -> Long.parseLong(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> { if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(); }
                case "JSON" -> { if (objectMapper.readTree(value) == null) throw new IllegalArgumentException(); }
                default -> { }
            }
            if (KNOWN_TYPES.containsKey(key) && ("INTEGER".equals(type) || "DECIMAL".equals(type))) {
                BigDecimal number = new BigDecimal(value);
                if (number.signum() < 0) throw new IllegalArgumentException();
                if (key.startsWith("spa.booking.") && number.compareTo(BigDecimal.valueOf(525600)) > 0) throw new IllegalArgumentException();
                if ("inventory.expiry_warning_days".equals(key) && number.compareTo(BigDecimal.valueOf(3650)) > 0) throw new IllegalArgumentException();
            }
            if ("spa.booking.no_show_quota_action".equals(key) && !Set.of("FORFEIT","RELEASE").contains(value)) throw new IllegalArgumentException();
            if ("spa.ticket.expiry_check_mode".equals(key) && !Set.of("BOOKING_TIME","SERVICE_START").contains(value)) throw new IllegalArgumentException();
            if ("spa.booking.policy_version".equals(key) && value.length() > 100) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            throw new BusinessException("Invalid value for config " + key + " (" + type + ")");
        }
        return value;
    }
}

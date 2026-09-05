package com.core.beautyshop.modules.inventory.api.exception;

import com.core.beautyshop.shared.exception.BusinessException;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(message);
    }
}

package com.core.beautyshop.shared.exception;

import lombok.Getter;

@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    }

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

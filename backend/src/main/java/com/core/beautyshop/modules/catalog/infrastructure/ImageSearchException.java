package com.core.beautyshop.modules.catalog.infrastructure;

public class ImageSearchException extends RuntimeException {
    private final int status;
    public ImageSearchException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
}

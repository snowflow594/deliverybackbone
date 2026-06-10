package com.estefano.deliverybackbone.common;

public class InsufficientStockException extends ConflictException {

    private final long productId;

    public InsufficientStockException(long productId, int requested) {
        super("Stock insuficiente para el producto %d (solicitado: %d)".formatted(productId, requested));
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}

package com.estefano.deliverybackbone.common;

/** Estado del recurso incompatible con la operación (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

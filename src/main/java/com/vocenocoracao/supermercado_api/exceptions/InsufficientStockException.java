package com.vocenocoracao.supermercado_api.exceptions;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}

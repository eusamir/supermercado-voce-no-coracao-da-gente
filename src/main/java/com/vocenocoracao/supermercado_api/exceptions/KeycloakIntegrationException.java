package com.vocenocoracao.supermercado_api.exceptions;

public class KeycloakIntegrationException extends RuntimeException {

    public KeycloakIntegrationException(String message) {
        super(message);
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

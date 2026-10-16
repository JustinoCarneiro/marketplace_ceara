package com.onda.marketplace.servicerequest;

public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException() {
        super("Armazenamento temporariamente indisponível.");
    }
}

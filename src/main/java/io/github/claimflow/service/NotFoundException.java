package io.github.claimflow.service;

/** A referenced claim or policy does not exist. */
public class NotFoundException extends RuntimeException {

    /** Creates the exception. */
    public NotFoundException(String message) {
        super(message);
    }
}

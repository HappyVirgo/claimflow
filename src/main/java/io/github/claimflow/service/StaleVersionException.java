package io.github.claimflow.service;

/** The client acted on an outdated version of a claim (optimistic-lock conflict). */
public class StaleVersionException extends RuntimeException {

    /** Creates the exception. */
    public StaleVersionException(String claimNumber, long expected, long actual) {
        super("Claim " + claimNumber + " is at version " + actual + " but the request expected " + expected);
    }
}

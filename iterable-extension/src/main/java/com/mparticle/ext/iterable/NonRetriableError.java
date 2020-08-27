package com.mparticle.ext.iterable;

import java.io.IOException;

/**
 * Exception thrown when batch processing fails and should not be retried.
 */
public class NonRetriableError extends IOException {

    public NonRetriableError() {
        super();
    }

    public NonRetriableError(String message) {
        super(message);
    }
}

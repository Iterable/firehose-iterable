package com.mparticle.ext.iterable;

import java.io.IOException;

/**
 * Exception thrown when batch processing fails and should not be retried.
 */
public class ProcessingError extends IOException {

    public ProcessingError() {
        super();
    }

    public ProcessingError(String message) {
        super(message);
    }
}

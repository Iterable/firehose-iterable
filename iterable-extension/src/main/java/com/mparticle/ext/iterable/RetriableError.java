package com.mparticle.ext.iterable;

import java.io.IOException;

/**
 * Exception thrown when batch processing fails and should be retried.
 */
public class RetriableError extends IOException {

  public RetriableError() {
    super();
  }

  public RetriableError(String message) {
    super(message);
  }
}

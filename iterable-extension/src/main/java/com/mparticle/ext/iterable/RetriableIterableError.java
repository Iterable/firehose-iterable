package com.mparticle.ext.iterable;

import java.io.IOException;

public class RetriableIterableError extends IOException {

  public RetriableIterableError() {
    super();
  }

  public RetriableIterableError(String message) {
    super(message);
  }
}

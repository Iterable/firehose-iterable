package com.mparticle.ext.iterable;

import java.io.IOException;

public class RetriableError extends IOException {

  public RetriableError() {
    super();
  }

  public RetriableError(String message) {
    super(message);
  }
}

package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
import com.mparticle.iterable.IterableErrorHandler;
import retrofit2.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IterableExtensionLogger {

  private static final Gson gson = new GsonBuilder().create();

  static void logApiError(Response<?> response, UUID id) {
    String iterableApiCode = null;
    try {
      IterableApiResponse errorBody = IterableErrorHandler.parseError(response);
      iterableApiCode = errorBody.code;
    } catch (IOException e) {
      iterableApiCode = "Unable to parse Iterable API code";
    }
    String requestId = id != null ? id.toString() : "Unavailable";
    String url = response.raw().request().url().encodedPath();
    String httpStatus = String.valueOf(response.code());
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("message", "Error sending request to Iterable");
    logMessage.put("url", url);
    logMessage.put("httpStatus", httpStatus);
    logMessage.put("iterableApiCode", iterableApiCode);
    logMessage.put("mParticleEventId", requestId);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public static void logError(String message) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("message", message);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }
}

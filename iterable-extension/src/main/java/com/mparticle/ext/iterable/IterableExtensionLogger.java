package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
import retrofit2.Response;

import java.util.HashMap;
import java.util.Map;

public class IterableExtensionLogger {

  private static final Gson gson = new GsonBuilder().create();

  public static void logApiError(
      String url, String httpStatus, String iterableApiCode, String eventId) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("message", "Error sending request to Iterable");
    logMessage.put("url", url);
    logMessage.put("httpStatus", httpStatus);
    logMessage.put("iterableApiCode", iterableApiCode);
    logMessage.put("mParticleEventId", eventId);

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

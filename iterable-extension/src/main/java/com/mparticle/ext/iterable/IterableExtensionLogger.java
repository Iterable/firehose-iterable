package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
import com.mparticle.iterable.IterableErrorHandler;
import retrofit2.Response;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A utility class for writing logs to the Lambda log stream. All static methods
 * write a JSON object to Standard Output where it can be queried in Cloudwatch.
 * Every log message contains the "awsRequestId" in order to connect each log
 * statements from a given invocation.
 */
public class IterableExtensionLogger {

  private static IterableExtensionLogger singleton = null;
  private static final Gson gson = new GsonBuilder().create();

  public static void logIterableApiError(Response<?> response, UUID mparticleEventId, String awsRequestId) {
    String iterableApiCode = null;
    try {
      IterableApiResponse errorBody = IterableErrorHandler.parseError(response);
      iterableApiCode = errorBody.code;
    } catch (IOException e) {
      iterableApiCode = "Unable to parse Iterable API code";
    }
    String requestId = mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
    String url = response.raw().request().url().encodedPath();
    String httpStatus = String.valueOf(response.code());
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "RetriableError");
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("message", "Received an error HTTP status from the Iterable API");
    logMessage.put("url", url);
    logMessage.put("httpStatus", httpStatus);
    logMessage.put("iterableApiCode", iterableApiCode);
    logMessage.put("mParticleEventId", requestId);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public static void logIterableApiTimeout(String url, UUID mparticleEventId, String awsRequestId) {
    String eventIdString = mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "RetriableError");
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("message", "Encountered a timeout while connecting to the Iterable API");
    logMessage.put("url", url);
    logMessage.put("mparticleEventId", eventIdString);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public static void logNonRetriableError(String message, String awsRequestId) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "NonRetriable");
    logMessage.put("awsRequestId", awsRequestId);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public static void logUnexpectedError(Exception e, String awsRequestId) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "UnexpectedError");
    logMessage.put("awsRequestId", awsRequestId);
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    logMessage.put("message", sw.toString());
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public static void logMessage(String message, String awsRequestId) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("message", message);
    logMessage.put("awsRequestId", awsRequestId);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }
}

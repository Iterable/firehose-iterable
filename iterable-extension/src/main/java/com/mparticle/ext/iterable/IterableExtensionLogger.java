package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
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

  private static final Gson gson = new GsonBuilder().create();
  private String awsRequestId;

  public IterableExtensionLogger(String awsRequestId) {
    this.awsRequestId = awsRequestId;
  }

  public void logIterableApiError(Response<?> response, UUID mparticleEventId, Boolean isRetriable) {
    String errorType = isRetriable ? "RetriableError" : "NonRetriableError";
    String requestId = mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
    String url = response.raw().request().url().encodedPath();
    String httpStatus = String.valueOf(response.code());

    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", errorType);
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mParticleEventId", requestId);
    logMessage.put("message", "Received a retriable HTTP error status code from the Iterable API");
    logMessage.put("url", url);
    logMessage.put("httpStatus", httpStatus);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logIterableApiTimeout(String url, UUID mparticleEventId) {
    String eventIdString = mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "RetriableError");
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mparticleEventId", eventIdString);
    logMessage.put("message", "Encountered a timeout while connecting to the Iterable API");
    logMessage.put("url", url);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logNonRetriableError(String message, UUID mparticleEventId) {
    String eventIdString = mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "NonRetriable");
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mparticleEventId", eventIdString);
    logMessage.put("message", message);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logUnexpectedError(Exception e) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", "UnexpectedError");
    logMessage.put("awsRequestId", awsRequestId);
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    logMessage.put("message", sw.toString());
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logMessage(String message) {
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("message", message);
    logMessage.put("awsRequestId", awsRequestId);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public String getAwsRequestId() {
    return awsRequestId;
  }

  public void setAwsRequestId(String id) {
    awsRequestId = id;
  }
}

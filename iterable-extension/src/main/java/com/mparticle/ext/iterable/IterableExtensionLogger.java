package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import retrofit2.Response;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A utility class for writing logs to the Lambda log stream. All static methods write a JSON object
 * to Standard Output where it can be queried in Cloudwatch. Every log message contains the
 * "awsRequestId" in order to connect each log statements from a given invocation. Logs containing
 * PII are sent to Blobby. 
 */
public class IterableExtensionLogger {

  public static final String RETRIABLE_HTTP_ERROR = "RetriableHTTPError";
  public static final String NON_RETRIABLE_HTTP_ERROR = "NonRetriableHTTPError";
  public static final String PROCESSING_ERROR = "ProcessingError";
  public static final String UNEXPECTED_ERROR = "UnexpectedError";
  private static final Gson gson = new GsonBuilder().create();
  private static final OkHttpClient httpClient = new OkHttpClient();
  private static final String BLOBBY_URL = "https://blobby.internal.prd-itbl.co/test/";
  private String awsRequestId;
  private String mparticleBatch;

  public IterableExtensionLogger(String awsRequestId) {
    this.awsRequestId = awsRequestId;
  }

  public void logIterableApiError(retrofit2.Call<?> preparedCall,
                                  Response<?> response, UUID mparticleEventId, Boolean isRetriable) {
    Map<String, String> blobbyLogMessage = new HashMap<>();
    blobbyLogMessage.put("mParticleBatch", mparticleBatch);
    blobbyLogMessage.put("request", preparedCall.request().body().toString());
    String responseBody;
    try {
      responseBody = response.errorBody().string();
    } catch (IOException e ) {
      responseBody = "Error";
    }
    blobbyLogMessage.put("response", responseBody);
    String blobbyId = logToBlobby(blobbyLogMessage);

    String errorType = isRetriable ? RETRIABLE_HTTP_ERROR : NON_RETRIABLE_HTTP_ERROR;
    String requestId = mparticleEventId != null ? mparticleEventId.toString() : "Error";
    String url = response.raw().request().url().encodedPath();
    String httpStatus = String.valueOf(response.code());

    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", errorType);
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mParticleEventId", requestId);
    logMessage.put("blobbyId", blobbyId);
    logMessage.put("message", "Received an error HTTP status code from the Iterable API");
    logMessage.put("url", url);
    logMessage.put("httpStatus", httpStatus);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logIterableApiTimeout(String url, UUID mparticleEventId) {
    String eventIdString = mparticleEventId != null ? mparticleEventId.toString() : "Error";
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", RETRIABLE_HTTP_ERROR);
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mparticleEventId", eventIdString);
    logMessage.put("message", "Encountered a timeout while connecting to the Iterable API");
    logMessage.put("url", url);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logProcessingError(String message, UUID mparticleEventId) {
    Map<String, String> blobbyLogMessage = new HashMap<>();
    blobbyLogMessage.put("mParticleBatch", mparticleBatch);
    String blobbyId = logToBlobby(blobbyLogMessage);

    String eventIdString = mparticleEventId != null ? mparticleEventId.toString() : "Error";
    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", PROCESSING_ERROR);
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("mparticleEventId", eventIdString);
    logMessage.put("blobbyId", blobbyId);
    logMessage.put("message", message);
    String messageJson = gson.toJson(logMessage);
    System.out.println(messageJson);
  }

  public void logUnexpectedError(Exception e) {
    Map<String, String> blobbyLogMessage = new HashMap<>();
    blobbyLogMessage.put("mParticleBatch", mparticleBatch);
    String blobbyId = logToBlobby(blobbyLogMessage);

    Map<String, String> logMessage = new HashMap<>();
    logMessage.put("errorType", UNEXPECTED_ERROR);
    logMessage.put("awsRequestId", awsRequestId);
    logMessage.put("blobbyId", blobbyId);
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

  public void setMparticleBatch(String mparticleBatch) {
    this.mparticleBatch = mparticleBatch;
  }

  private static String logToBlobby(Map<String, String> message) {
    RequestBody body = RequestBody.create(MediaType.parse("application/json"), gson.toJson(message));
    Request request = new Request.Builder().url(BLOBBY_URL).post(body).build();
    okhttp3.Call call = httpClient.newCall(request);

    String blobbyId;
    try {
      okhttp3.Response response = call.execute();
      blobbyId = response.body().string();
    } catch (Exception e) {
      blobbyId = "Error";
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      System.out.println(sw.toString());
    }
    return blobbyId;
  }
}

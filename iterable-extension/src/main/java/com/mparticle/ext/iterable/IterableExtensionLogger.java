package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
import com.mparticle.iterable.IterableErrorHandler;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import retrofit2.Response;

/**
 * A utility class for logging state while processing mParticle batches. All static methods write a
 * JSON object to Standard Output where it can be queried in Cloudwatch.
 */
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

    static void logApiTimeout(String url, UUID mparticleEventId) {
        String eventIdString =
                mparticleEventId != null ? mparticleEventId.toString() : "Unavailable";
        Map<String, String> logMessage = new HashMap<>();
        logMessage.put("errorType", "Retriable");
        logMessage.put("message", "A timeout occurred while making request to Iterable");
        logMessage.put("url", url);
        logMessage.put("mparticleEventId", eventIdString);
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

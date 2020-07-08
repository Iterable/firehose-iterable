package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.IterableApiResponse;
import retrofit2.Response;

import java.util.HashMap;
import java.util.Map;

public class IterableExtensionLogger {

    private static final Gson gson = new GsonBuilder().create();

    public static void logApiError(Response<IterableApiResponse> response,
                                   IterableApiResponse errorBody, String eventId) {
        Map<String, String> logMessage = new HashMap<>();
        logMessage.put("message", "Error sending request to Iterable");
        logMessage.put("url", response.raw().request().url().encodedPath());
        logMessage.put("httpStatus", String.valueOf(response.code()));
        logMessage.put("iterableApiCode", errorBody.code);
        logMessage.put("mParticleEventId", eventId);

        String messageJson = gson.toJson(logMessage);
        System.out.println(messageJson);
    }
}

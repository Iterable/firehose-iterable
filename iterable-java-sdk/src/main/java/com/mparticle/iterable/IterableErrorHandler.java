package com.mparticle.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import retrofit2.Response;

public class IterableErrorHandler {

    private static final Gson gson = new GsonBuilder().create();

    public static IterableApiResponse parseError(Response<?> response) {
        IterableApiResponse apiResponse = new IterableApiResponse();

        try {
            apiResponse = gson.fromJson(response.errorBody().string(), apiResponse.getClass());
            return apiResponse;
        } catch (Exception e) {
            return apiResponse;
        }
    }
}

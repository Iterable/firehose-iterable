package com.mparticle.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import retrofit2.Response;

import java.io.IOException;

public class IterableErrorHandler {

    private static final Gson gson = new GsonBuilder().create();

    public static IterableApiResponse parseError(Response<?> response) throws IOException {

        if (response.errorBody() == null) {
            throw new IOException("The response did not contain an errorBody");
        }

        try {
            String responseJson = response.errorBody().string();
            IterableApiResponse apiResponse = new IterableApiResponse();
            apiResponse = gson.fromJson(responseJson, apiResponse.getClass());
            return apiResponse;
        } catch (IOException e) {
            throw new IOException("Unable to read response body while parsing Iterable API error");
        } catch (JsonSyntaxException e) {
            throw new IOException("Unable to deserialize Iterable API error");
        }
    }
}

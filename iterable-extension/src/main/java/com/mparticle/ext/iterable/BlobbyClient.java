package com.mparticle.ext.iterable;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

/**
 * Simple client for making requests to Blobby
 */
public class BlobbyClient {
    private static final int TIMEOUT_SECONDS = 10;
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    private static final String BLOBBY_URL = "https://blobby.internal.prd-itbl.co/mparticle_logs";

    public String log(String msg) {
        RequestBody body = RequestBody.create(MediaType.parse("text/plain"), msg);
        Request request = new Request.Builder()
                .url(BLOBBY_URL)
                .post(body)
                .build();
        okhttp3.Call call = httpClient.newCall(request);

        String blobbyId;
        try {
            okhttp3.Response response = call.execute();
            blobbyId = response.body().string();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            blobbyId = sw.toString();
        }
        return blobbyId;
    }
}

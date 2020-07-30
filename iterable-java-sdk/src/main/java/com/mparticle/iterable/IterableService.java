package com.mparticle.iterable;



import okhttp3.*;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Iterable API defined here:
 *
 * https://api.iterable.com/api/docs
 *
 */
public interface IterableService {

    String HOST = "api.iterable.com";
    String PARAM_API_KEY = "api_key";
    long SERVICE_TIMEOUT_SECONDS = 60;

    @POST("api/events/track")
    Call<IterableApiResponse> track(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body TrackRequest trackRequest);

    @POST("api/events/trackPushOpen")
    Call<IterableApiResponse> trackPushOpen(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body TrackPushOpenRequest registerRequest);

    @POST("api/users/update")
    Call<IterableApiResponse> userUpdate(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body UserUpdateRequest trackRequest);

    @POST("api/users/updateEmail")
    Call<IterableApiResponse> updateEmail(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body UpdateEmailRequest updateEmailRequest);

    @POST("api/users/registerDeviceToken")
    Call<IterableApiResponse> registerToken(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body RegisterDeviceTokenRequest registerRequest);

    @POST("api/lists/subscribe")
    Call<ListResponse> listSubscribe(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body SubscribeRequest subscribeRequest);

    @POST("api/lists/unsubscribe")
    Call<ListResponse> listUnsubscribe(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body UnsubscribeRequest unsubscribeRequest);

    @POST("api/commerce/trackPurchase")
    Call<IterableApiResponse> trackPurchase(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body TrackPurchaseRequest purchaseRequest);

    @POST("api/users/updateSubscriptions")
    Call<IterableApiResponse> updateSubscriptions(@Query(IterableService.PARAM_API_KEY) String apiKey, @Body UpdateSubscriptionsRequest userUpdateRequest);

    /**
     * At the moment this is only used for unit testing the list subscribe/unsubscribe API calls
     */
    @GET("api/lists")
    Call<GetListResponse> lists();

    class HeadersInterceptor implements Interceptor {
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request original = chain.request();
            Request requestWithHeaders = original.newBuilder()
                    .header("User-Agent", "mparticle-lambda")
                    .build();
            return chain.proceed(requestWithHeaders);
        }
    }

    static IterableService newInstance() {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new HeadersInterceptor())
                .connectTimeout(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        final HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(IterableService.HOST)
                .build();
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(IterableService.class);
    }
}
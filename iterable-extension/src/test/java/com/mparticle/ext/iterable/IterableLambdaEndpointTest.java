package com.mparticle.ext.iterable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mparticle.iterable.IterableApiResponse;
import com.mparticle.iterable.IterableService;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IterableLambdaEndpointTest {

    private static final String TEST_INPUT = "{\r\n\t\"type\": \"event_processing_request\",\r\n\t\"id\": \"9365387f-9638-4fb2-88a9-36db9be1be5d\",\r\n\t\"timestamp_ms\": 1591328642162,\r\n\t\"firehose_version\": \"2.2.0\",\r\n\t\"account\": {\r\n\t\t\"account_id\": 0,\r\n\t\t\"account_settings\": {\r\n\t\t\t\"apiKey\": \"foo\",\r\n\t\t\t\"userIdField\": \"customerId\"\r\n\t\t}\r\n\t},\r\n\t\"user_identities\": [{\r\n\t\t\"type\": \"email\",\r\n\t\t\"encoding\": \"raw\",\r\n\t\t\"value\": \"foo@placeholder.email\"\r\n\t}],\r\n\t\"events\": [{\r\n\t\t\"type\": \"user_attribute_change\",\r\n\t\t\"id\": \"a0ab9fd1-f5b2-4119-b08a-dab1669b852d\",\r\n\t\t\"timestamp_ms\": 2\r\n\t}, {\r\n\t\t\"type\": \"user_attribute_change\",\r\n\t\t\"id\": \"ba66bb9f-1770-4006-8072-3351a7449be4\",\r\n\t\t\"timestamp_ms\": 3\r\n\t}],\r\n\t\"device_application_stamp\": \"foo\",\r\n\t\"mpid\": \"1234567890\"\r\n}";

    private IterableExtension extension;
    private IterableLambdaEndpoint lambda;

    @Before
    public void setup() throws IOException {
        extension = new IterableExtension();
        lambda = new IterableLambdaEndpoint();
    }

    @Test
    public void testSuccessResponse() throws IOException {
        // Mock successful calls to Iterable
        lambda.processor.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(lambda.processor.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> iterableResponse = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(iterableResponse);

        InputStream input = new ByteArrayInputStream(TEST_INPUT.getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        lambda.handleRequest(input, output, null);
        ObjectMapper mapper = new ObjectMapper();
        SuccessResponse lambdaResponse = mapper.readValue(new String(output.toByteArray()), SuccessResponse.class);
        assertEquals(200, lambdaResponse.getStatusCode());
        assertNotNull(lambdaResponse.getBody().getFirehoseVersion());
    }

    @Test
    public void testTooManyRequestsResponse() throws IOException {
        // Mock 429 response from Iterable
        lambda.processor.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(lambda.processor.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Response iterableResponse = Response.error(429, ResponseBody.create(null, ""));
        Mockito.when(callMock.execute()).thenReturn(iterableResponse);

        InputStream input = new ByteArrayInputStream(TEST_INPUT.getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        lambda.handleRequest(input, output, null);

        ObjectMapper mapper = new ObjectMapper();
        ErrorResponse lambdaResponse = mapper.readValue(new String(output.toByteArray()), ErrorResponse.class);
        assertEquals(429, lambdaResponse.getStatusCode());
        assertNotNull(lambdaResponse.getBody());
    }

    @Test
    public void testGenericErrorResponse() throws IOException {
        // Mock non-429 error response from Iterable
        lambda.processor.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(lambda.processor.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Response iterableResponse = Response.error(400, ResponseBody.create(null, ""));
        Mockito.when(callMock.execute()).thenReturn(iterableResponse);

        InputStream input = new ByteArrayInputStream(TEST_INPUT.getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        lambda.handleRequest(input, output, null);

        ObjectMapper mapper = new ObjectMapper();
        ErrorResponse lambdaResponse = mapper.readValue(new String(output.toByteArray()), ErrorResponse.class);
        assertEquals(500, lambdaResponse.getStatusCode());
        assertNotNull(lambdaResponse.getBody());
    }
}

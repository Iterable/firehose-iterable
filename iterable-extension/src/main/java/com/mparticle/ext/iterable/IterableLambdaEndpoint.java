package com.mparticle.ext.iterable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;


public class IterableLambdaEndpoint implements RequestStreamHandler {

    static MessageSerializer serializer = new MessageSerializer();
    static IterableExtension processor = new IterableExtension();

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        Message request = serializer.deserialize(input, Message.class);

        try {
            Message message = processor.processMessage(request);
            SuccessResponse success = new SuccessResponse(200, message);
            serializer.serialize(output, success);
        } catch (TooManyRequestsException e) {
            Map<String, String> body = new HashMap<>();
            body.put("message", "Iterable rate limit exceeded");
            ErrorResponse error = new ErrorResponse(429, body);
            serializer.serialize(output, error);
        } catch (IOException e) {
            Map<String, String> body = new HashMap<>();
            body.put("message", e.getMessage());
            ErrorResponse error = new ErrorResponse(500, body);
            serializer.serialize(output, error);
        }
    }
}
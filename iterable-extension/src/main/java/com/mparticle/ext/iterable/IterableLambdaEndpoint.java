package com.mparticle.ext.iterable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class IterableLambdaEndpoint implements RequestStreamHandler {

    static MessageSerializer serializer = new MessageSerializer();
    static IterableExtension processor = new IterableExtension();

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        // TODO - remove
        String messageBody = IOUtils.toString(input, "UTF-8");
        System.out.println("Received message from queue: " + messageBody);

        Message request = serializer.deserialize(input, Message.class);
        Message response = processor.processMessage(request);
        serializer.serialize(output, response);
    }
}
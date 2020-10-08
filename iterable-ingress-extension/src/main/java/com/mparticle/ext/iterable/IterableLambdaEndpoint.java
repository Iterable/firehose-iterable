package com.mparticle.ext.iterable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;

public class IterableLambdaEndpoint implements RequestStreamHandler {

    public static final MessageSerializer serializer = new MessageSerializer();
    public static final IngressExtension processor = new IngressExtension();
    public IngressQueueManager queueManager;

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
            throws IOException {
        if (queueManager == null) {
            queueManager = IngressQueueManager.create();
        }
        String mparticleRequest = IOUtils.toString(input, "UTF-8");
        queueManager.enqueueMessage(mparticleRequest);

        Message request = serializer.deserialize(mparticleRequest, Message.class);
        Message response = processor.processMessage(request);
        serializer.serialize(output, response);
    }
}

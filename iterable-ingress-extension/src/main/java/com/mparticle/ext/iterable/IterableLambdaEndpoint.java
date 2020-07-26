package com.mparticle.ext.iterable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IterableLambdaEndpoint implements RequestStreamHandler {

  public static final MessageSerializer serializer = new MessageSerializer();
  public static final IngressExtension processor = new IngressExtension();
  public SqsClient sqsClient;
  public String queueUrl;
  public IngressQueueManager queueManager;

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    // Add the request to SQS
    if (queueManager == null) {
      queueManager = IngressQueueManager.create();
    }
    queueManager.enqueueMessage(input);

    // Return an mParticle response object to the invoker
    Message request = serializer.deserialize(input, Message.class);
    Message response = processor.processMessage(request);
    serializer.serialize(output, response);
  }
}

package com.mparticle.ext.iterable.ingress;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;

public class IngressLambdaEndpoint implements RequestStreamHandler {

  public static final MessageSerializer serializer = new MessageSerializer();
  public static final IterableExtensionIngress processor = new IterableExtensionIngress();
  public SqsClient sqsClient;
  public String queueUrl;

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    // Add the request to SQS
    enqueueMessage(input);

    // Return an mParticle response object to the invoker
    Message request = serializer.deserialize(input, Message.class);
    Message response = processor.processMessage(request);
    serializer.serialize(output, response);
  }

  public void enqueueMessage(InputStream input) {
    if (queueUrl == null) {
      queueUrl = Objects.requireNonNull(System.getenv("QUEUE_URL"));
    }
    if (sqsClient == null) {
      sqsClient = SqsClient.builder()
              .region(Region.US_EAST_1)
              .build();
    }
    String mparticleRequest = convertInputToString(input);
    // TODO: remove log statement before sending live traffic
    System.out.println("Adding message to SQS: " + mparticleRequest);
    SendMessageRequest req = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(mparticleRequest)
            .build();
    sqsClient.sendMessage(req);
  }

  public static String convertInputToString(InputStream input) {
    String outputString = null;
    try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8.name())) {
      outputString = scanner.useDelimiter("\\A").next();
    }
    return outputString;
  }
}

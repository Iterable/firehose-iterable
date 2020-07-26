package com.mparticle.ext.iterable;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;

public class IngressQueueManager {
  private SqsClient sqsClient;
  private String queueUrl;

  public IngressQueueManager(SqsClient sqsClient, String queueUrl) {
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
  }

  public void enqueueMessage(InputStream input) {
    String mparticleRequest = convertInputToString(input);
    // TODO: remove log statement before sending live traffic
    System.out.println("Adding message to SQS: " + mparticleRequest);
    SendMessageRequest req =
        SendMessageRequest.builder().queueUrl(queueUrl).messageBody(mparticleRequest).build();
    sqsClient.sendMessage(req);
  }

  public static String convertInputToString(InputStream input) {
    String outputString = null;
    try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8.name())) {
      outputString = scanner.useDelimiter("\\A").next();
    }
    return outputString;
  }

  public static IngressQueueManager create() {
    SqsClient sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
    String queueUrl = Objects.requireNonNull(System.getenv("QUEUE_URL"));
    return new IngressQueueManager(sqsClient, queueUrl);
  }
}

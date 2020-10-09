package com.mparticle.ext.iterable;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Objects;

public class IngressQueueManager {
  public static final long CLIENT_TIMEOUT_SECONDS = 7L;
  public static final int NUM_RETRIES = 5;
  private final SqsClient sqsClient;
  private final String queueUrl;

  public IngressQueueManager(SqsClient sqsClient, String queueUrl) {
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
  }

  public void enqueueMessage(String message) {
    SendMessageRequest req =
            SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build();

    int attempt = 0;
    while (attempt < NUM_RETRIES) {
      attempt++;
      try {
        sqsClient.sendMessage(req);
        break;
      } catch (SdkClientException e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        System.out.println("Failed attempt " + (attempt + 1) + " to upload message to SQS  \n" + sw.toString());
        if (attempt == NUM_RETRIES - 1) {
          throw e;
        }
      }
    }
  }

  public static IngressQueueManager create() {
    ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(CLIENT_TIMEOUT_SECONDS))
            .build();
    SqsClient sqsClient = SqsClient.builder()
            .region(Region.US_EAST_1)
            .overrideConfiguration(clientConfig)
            .build();
    String queueUrl = Objects.requireNonNull(System.getenv("QUEUE_URL"));
    return new IngressQueueManager(sqsClient, queueUrl);
  }
}

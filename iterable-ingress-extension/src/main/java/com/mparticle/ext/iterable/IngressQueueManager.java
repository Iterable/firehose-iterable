package com.mparticle.ext.iterable;

import java.util.Objects;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class IngressQueueManager {
    private SqsClient sqsClient;
    private String queueUrl;

    public IngressQueueManager(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    public void enqueueMessage(String message) {
        SendMessageRequest req =
                SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build();
        sqsClient.sendMessage(req);
    }

    public static IngressQueueManager create() {
        SqsClient sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
        String queueUrl = Objects.requireNonNull(System.getenv("QUEUE_URL"));
        return new IngressQueueManager(sqsClient, queueUrl);
    }
}

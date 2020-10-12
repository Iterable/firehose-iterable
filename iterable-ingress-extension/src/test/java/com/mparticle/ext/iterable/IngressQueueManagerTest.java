package com.mparticle.ext.iterable;

import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;


public class IngressQueueManagerTest {

    @Test
    public void testEnqueueMessageDoesNotRetrySuccess() {
        SqsClient mockClient = Mockito.mock(SqsClient.class);
        Mockito.when(mockClient.sendMessage(Mockito.any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());
        IngressQueueManager queueManager = new IngressQueueManager(mockClient, "foo");

        queueManager.enqueueMessage("foo");
        Mockito.verify(mockClient, Mockito.times(1)).sendMessage(Mockito.any(SendMessageRequest.class));
    }

    @Test
    public void testEnqueueMessageRetriesFailure() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        SqsClient mockClient = Mockito.mock(SqsClient.class);
        Mockito.when(mockClient.sendMessage(Mockito.any(SendMessageRequest.class)))
                .thenThrow(SdkClientException.class);
        IngressQueueManager queueManager = new IngressQueueManager(mockClient, "foo");

        try {
            queueManager.enqueueMessage("foo");
        } catch (SdkClientException e) {
            // ignored
        } finally {
            assertEquals(IngressQueueManager.NUM_RETRIES, Mockito.mockingDetails(mockClient).getInvocations().size());
        }
        System.setOut(System.out);
    }
}

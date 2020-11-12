package com.mparticle.ext.iterable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IterableLambdaEndpoint implements RequestStreamHandler {

  static final MessageSerializer serializer = new MessageSerializer();
  static final ObjectMapper mapper = new ObjectMapper();
  static final BlobbyClient blobbyClient = new BlobbyClient();

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws RetriableError {
    IterableExtensionLogger logger = new IterableExtensionLogger(context.getAwsRequestId(), blobbyClient, false);
    IterableExtension extension = new IterableExtension(logger);

    try {
      String inputString = IOUtils.toString(input, "UTF-8");
      logger.setMparticleBatch(inputString);
      Message request = parseQueueTrigger(inputString);
      Message response = extension.processMessage(request);
      serializer.serialize(output, response);
    } catch (ProcessingError e) {
      logger.logMessage("Invocation terminated by a " + logger.PROCESSING_ERROR);
    } catch (RetriableError e) {
      logger.logMessage("Invocation terminated by a " + logger.RETRIABLE_HTTP_ERROR);
      // When an unhandled exception occurs, the current message isn't deleted from the queue and will be automatically retried.
      throw e;
    } catch (Exception e) {
      logger.logMessage("Invocation terminated by an " + logger.UNEXPECTED_ERROR);
      logger.logUnexpectedError(e);
    }
  }

  public static Message parseQueueTrigger(String triggerString) throws IOException {
    QueueTrigger trigger = mapper.readValue(triggerString, QueueTrigger.class);
    String messageBody = trigger.records.get(0).body;
    Message request = serializer.deserialize(messageBody, Message.class);
    return request;
  }
}

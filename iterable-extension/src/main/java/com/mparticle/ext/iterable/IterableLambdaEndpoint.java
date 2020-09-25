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

  static MessageSerializer serializer = new MessageSerializer();
  static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws RetriableError {
    IterableExtensionLogger logger = new IterableExtensionLogger(context.getAwsRequestId());
    IterableExtension processor = new IterableExtension(logger);

    try {
      String inputString = IOUtils.toString(input, "UTF-8");
      Message request = parseQueueTrigger(inputString);
      Message response = processor.processMessage(request);
      serializer.serialize(output, response);
    } catch (NonRetriableError e) {
      logger.logMessage("Invocation terminated by a NonRetriableError");
    } catch (RetriableError e) {
      logger.logMessage("Invocation terminated by a RetriableError");
      throw e;
    } catch (Exception e) {
      logger.logMessage("Invocation terminated by an UnexpectedError");
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

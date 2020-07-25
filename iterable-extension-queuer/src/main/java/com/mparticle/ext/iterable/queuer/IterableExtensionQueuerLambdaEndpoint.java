package com.mparticle.ext.iterable.queuer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.mparticle.sdk.model.Message;
import com.mparticle.sdk.model.MessageSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IterableExtensionQueuerLambdaEndpoint implements RequestStreamHandler {

  static MessageSerializer serializer = new MessageSerializer();
  static IterableExtensionQueuer processor = new IterableExtensionQueuer();

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    Message request = serializer.deserialize(input, Message.class);
    Message response = processor.processMessage(request);
    serializer.serialize(output, response);
  }
}

package com.mparticle.ext.iterable;

import static org.junit.Assert.assertEquals;

import com.mparticle.sdk.model.Message;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class IterableLambdaEndpointTest {
    static IterableLambdaEndpoint lambda = new IterableLambdaEndpoint();
    private static final String PATH_TO_FIXTURES = "src/test/resources/";

    @Test
    public void testParseQueueTrigger() throws IOException {
        String inputString = IOUtils.toString(readTestFixture("queueTrigger.json"), "UTF-8");
        Message request = lambda.parseQueueTrigger(inputString);
        assertEquals(Message.Type.EVENT_PROCESSING_REQUEST, request.getType());
    }

    private static InputStream readTestFixture(String fileName) throws IOException {
        File initialFile = new File(PATH_TO_FIXTURES + fileName);
        InputStream fixtureInputStream = new FileInputStream(initialFile);
        return fixtureInputStream;
    }
}

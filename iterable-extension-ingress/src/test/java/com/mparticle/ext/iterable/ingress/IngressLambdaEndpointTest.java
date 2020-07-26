package com.mparticle.ext.iterable.ingress;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.*;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

public class IngressLambdaEndpointTest {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String PATH_TO_FIXTURES = "src/test/resources/";
  private IngressLambdaEndpoint lambda;

  @Before
  public void setup() {
    lambda = new IngressLambdaEndpoint();
    lambda.queueUrl = "";
    lambda.sqsClient = Mockito.mock(SqsClient.class);
  }

  @Test
  @Ignore
  public void testProcessEventProcessingRequest() throws IOException {
    InputStream input = readTestFixture("EventProcessingRequest_ios.json");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Map<String, Object> expectedResponse = getFixtureAsMap("EventProcessingResponse.json");
    lambda.handleRequest(input, output, null);
    Map<String, Object> lambdaResponse =
        mapper.readValue(output.toString(), new TypeReference<Map<String, Object>>() {});
    for (String key : expectedResponse.keySet()) {
      assertNotNull(lambdaResponse.get(key));
    }
  }

  @Test
  @Ignore
  public void testProcessModuleRegistrationRequest() throws IOException {
    InputStream input = readTestFixture("ModuleRegistrationRequest.json");
    Map<String, Object> expectedResponse = getFixtureAsMap("ModuleRegistrationResponse.json");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    lambda.handleRequest(input, output, null);
    Map<String, Object> lambdaResponse =
            mapper.readValue(output.toString(), new TypeReference<Map<String, Object>>() {});
    for (String key : expectedResponse.keySet()) {
      assertNotNull(lambdaResponse.get(key));
    }
  }

  @Test
  @Ignore
  public void testProcessAudienceMembershipChangeRequest() throws IOException {
    InputStream input = readTestFixture("AudienceMembershipChangeRequest.json");
    Map<String, Object> expectedResponse = getFixtureAsMap("AudienceMembershipChangeResponse.json");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    lambda.handleRequest(input, output, null);
    Map<String, Object> lambdaResponse =
            mapper.readValue(output.toString(), new TypeReference<Map<String, Object>>() {});
    for (String key : expectedResponse.keySet()) {
      assertNotNull(lambdaResponse.get(key));
    }
  }

  private static Map<String, Object> getFixtureAsMap(String fileName) throws IOException {
    InputStream fixture = readTestFixture(fileName);
    Map<String, Object> fixtureMap =
            mapper.readValue(fixture, new TypeReference<Map<String, Object>>() {});
    return fixtureMap;
  }

  private static InputStream readTestFixture(String fileName) throws IOException {
    File initialFile = new File(PATH_TO_FIXTURES + fileName);
    InputStream fixtureInputStream = new FileInputStream(initialFile);
    return fixtureInputStream;
  }
}

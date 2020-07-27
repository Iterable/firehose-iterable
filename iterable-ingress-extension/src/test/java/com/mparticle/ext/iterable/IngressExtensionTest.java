package com.mparticle.ext.iterable;

import com.mparticle.sdk.model.MessageSerializer;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeResponse;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.*;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static com.mparticle.ext.iterable.IngressExtension.SETTING_API_KEY;
import static com.mparticle.ext.iterable.IngressExtension.SETTING_USER_ID_FIELD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IngressExtensionTest {

  private static final String TEST_API_KEY = "foo api key";
  private static MessageSerializer serializer;
  private static IngressExtension ingressExtension;

  @Before
  public void setup() {
    serializer = new MessageSerializer();
    ingressExtension = new IngressExtension();
  }

  @Test
  public void testProcessRegistrationRequest() throws Exception {
    ModuleRegistrationResponse response =
        ingressExtension.processRegistrationRequest(new ModuleRegistrationRequest());
    List<UserIdentityPermission> userIdentities = response.getPermissions().getUserIdentities();
    assertEquals(2, userIdentities.size());
    boolean email, customer;
    email =
        userIdentities.get(0).getType().equals(UserIdentity.Type.EMAIL)
            || userIdentities.get(1).getType().equals(UserIdentity.Type.EMAIL);

    customer =
        userIdentities.get(0).getType().equals(UserIdentity.Type.CUSTOMER)
            || userIdentities.get(1).getType().equals(UserIdentity.Type.CUSTOMER);

    assertTrue("Iterable Extension should register for email permission", email);
    assertTrue("Iterable Extension should register for customer id permission", customer);

    List<Setting> accountSettings = response.getEventProcessingRegistration().getAccountSettings();
    assertTrue(
        "There should be a single text setting (api key) for iterable",
        accountSettings.get(0).getType().equals(Setting.Type.TEXT));

    List<Event.Type> eventTypes =
        response.getEventProcessingRegistration().getSupportedEventTypes();
    assertTrue(
        "Iterable should support custom events", eventTypes.contains(Event.Type.CUSTOM_EVENT));
    assertTrue(
        "Iterable should support push subscriptions",
        eventTypes.contains(Event.Type.PUSH_SUBSCRIPTION));
    assertTrue(
        "Iterable should support push receipts",
        eventTypes.contains(Event.Type.PUSH_MESSAGE_RECEIPT));
    assertTrue(
        "Iterable should support user identity changes",
        eventTypes.contains(Event.Type.USER_IDENTITY_CHANGE));

    Setting setting =
        response.getAudienceProcessingRegistration().getAudienceConnectionSettings().get(0);
    assertTrue(
        "Iterable audiences should have a single Integer setting",
        setting.getType().equals(Setting.Type.INTEGER));
  }

  @Test
  public void testProcessEventProcessingRequest() throws Exception {
    EventProcessingRequest request = createEventProcessingRequest();
    List<Event> events = new LinkedList<>();
    events.add(new UserAttributeChangeEvent());
    request.setEvents(events);

    EventProcessingResponse response = ingressExtension.processEventProcessingRequest(request);
    System.out.println();
    System.out.println(serializer.serialize(response));
    System.out.println();
  }

  @Test
  public void testProcessAudienceMembershipChangeRequest() throws Exception {
    AudienceMembershipChangeResponse response =
        ingressExtension.processAudienceMembershipChangeRequest(new AudienceMembershipChangeRequest());
    System.out.println();
    System.out.println(serializer.serialize(response));
    System.out.println();
  }

  private EventProcessingRequest createEventProcessingRequest() {
    EventProcessingRequest request = new EventProcessingRequest();
    Account account = new Account();
    HashMap<String, String> settings = new HashMap<String, String>();
    settings.put(SETTING_API_KEY, TEST_API_KEY);
    settings.put(SETTING_USER_ID_FIELD, "customerId");
    account.setAccountSettings(settings);
    request.setAccount(account);
    request.setMpId("1234567890");

    return request;
  }
}

package com.mparticle.ext.iterable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mparticle.iterable.*;
import com.mparticle.sdk.model.audienceprocessing.Audience;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.UserProfile;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.Account;
import okhttp3.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.*;

import static com.mparticle.ext.iterable.IterableExtension.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class IterableExtensionTest {
    private static final String TEST_API_KEY = "foo api key";
    private IterableExtension testExtension;
    private IterableExtensionLogger testLogger;
    private IterableService iterableServiceMock;
    private Call callMock;
    private Audience testAudienceAddition1;
    private Audience testAudienceAddition2;
    private Audience testAudienceDeletion3;
    private List<Audience> testAudienceList;
    private UserProfile testUserProfile;
    private UserProfile testUserProfileWithEmail;
    private UserProfile testUserProfileWithEmailAndCustomerId;
    private Account testAccount;
    private IterableApiResponse testIterableApiSuccess;
    private Response testSuccessResponse;
    private LinkedList<UserIdentity> userIdentitiesWithEmail;
    private LinkedList<UserIdentity> userIdentitiesWithEmailAndCustomerId;
    private Gson gson;

    @Before
    public void setup() {
        testLogger = new IterableExtensionLogger("foo");
        testExtension = new IterableExtension(testLogger);
        iterableServiceMock = Mockito.mock(IterableService.class);
        callMock = Mockito.mock(Call.class);

        testAudienceAddition1 = new Audience();
        Map<String, String> audienceSubscriptionSettings = new HashMap<>();
        audienceSubscriptionSettings.put(IterableExtension.SETTING_LIST_ID, "1");
        testAudienceAddition1.setAudienceSubscriptionSettings(audienceSubscriptionSettings);
        testAudienceAddition1.setAudienceAction(Audience.AudienceAction.ADD);
        testAudienceAddition2 = new Audience();
        Map<String, String> audienceSubscriptionSettings2 = new HashMap<>();
        audienceSubscriptionSettings2.put(IterableExtension.SETTING_LIST_ID, "2");
        testAudienceAddition2.setAudienceSubscriptionSettings(audienceSubscriptionSettings2);
        testAudienceAddition2.setAudienceAction(Audience.AudienceAction.ADD);
        testAudienceDeletion3 = new Audience();
        Map<String, String> audienceSubscriptionSettings3 = new HashMap<>();
        audienceSubscriptionSettings3.put(IterableExtension.SETTING_LIST_ID, "3");
        testAudienceDeletion3.setAudienceSubscriptionSettings(audienceSubscriptionSettings3);
        testAudienceDeletion3.setAudienceAction(Audience.AudienceAction.DELETE);
        testAudienceList = new LinkedList<>();
        testAudienceList.add(testAudienceAddition2);
        testAudienceList.add(testAudienceAddition1);
        testAudienceList.add(testAudienceDeletion3);

        testUserProfile = new UserProfile();
        testUserProfileWithEmail = new UserProfile();
        userIdentitiesWithEmail = new LinkedList<>();
        userIdentitiesWithEmail.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW,
                "email_only@iterable.com"));
        testUserProfileWithEmail.setUserIdentities(userIdentitiesWithEmail);
        testUserProfileWithEmailAndCustomerId = new UserProfile();
        userIdentitiesWithEmailAndCustomerId = new LinkedList<>();
        userIdentitiesWithEmailAndCustomerId.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW,
                "email_and_id@iterable.com"));
        userIdentitiesWithEmailAndCustomerId.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW,
                "c1"));
        testUserProfileWithEmailAndCustomerId.setUserIdentities(userIdentitiesWithEmailAndCustomerId);

        testAccount = new Account();
        Map<String, String> accountSettings = new HashMap<>();
        accountSettings.put(SETTING_API_KEY, "some api key");
        testAccount.setAccountSettings(accountSettings);

        testIterableApiSuccess = new IterableApiResponse();
        testIterableApiSuccess.code = IterableApiResponse.SUCCESS_MESSAGE;
        testSuccessResponse = Response.success(testIterableApiSuccess);
        gson = new GsonBuilder().create();
    }

    @Test
    public void testProcessEventProcessingRequest() {
        EventProcessingRequest request = createEventProcessingRequest();
        List<Event> events = new LinkedList<>();
        Event customEvent1 = new UserAttributeChangeEvent();
        customEvent1.setTimestamp(3);
        Event customEvent2 = new UserAttributeChangeEvent();
        customEvent2.setTimestamp(2);
        Event customEvent3 = new UserAttributeChangeEvent();
        customEvent3.setTimestamp(1);
        Event customEvent4 = new UserAttributeChangeEvent();
        customEvent4.setTimestamp(4);
        request.setDeviceApplicationStamp("foo");
        //out of order
        events.add(customEvent1);
        events.add(customEvent2);
        events.add(customEvent3);
        events.add(customEvent4);

        request.setEvents(events);
        try {
            testExtension.processEventProcessingRequest(request);
        } catch (IOException e) {
            // Without an API key, this will throw because the extension isn't mocked.
        }
        assertNotNull("IterableService should have been created", testExtension.iterableService);

        assertEquals("Events should have been in order",1, request.getEvents().get(0).getTimestamp());
        assertEquals("Events should have been in order",2, request.getEvents().get(1).getTimestamp());
        assertEquals("Events should have been in order",3, request.getEvents().get(2).getTimestamp());
        assertEquals("Events should have been in order",4, request.getEvents().get(3).getTimestamp());
    }

    @Test
    public void testUpdateUser() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        EventProcessingRequest request = createEventProcessingRequest();
        //no user identities, no API call
        testExtension.updateUser(request);
        UserUpdateRequest userUpdateRequest = new UserUpdateRequest();
        Mockito.verify(testExtension.iterableService, never()).userUpdate(TEST_API_KEY, userUpdateRequest);

        //user identities but no email/userid, no API call
        List<UserIdentity> identities = new LinkedList<>();
        identities.add(new UserIdentity(UserIdentity.Type.FACEBOOK, Identity.Encoding.RAW, "123456"));
        request.setUserIdentities(identities);
        testExtension.updateUser(request);
        Mockito.verify(testExtension.iterableService, never()).userUpdate(TEST_API_KEY, userUpdateRequest);

        //ok, now we should get a single API call
        identities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        identities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        Map<String, String> userAttributes = new HashMap<String, String>();
        userAttributes.put("some attribute key", "some attribute value");
        request.setUserAttributes(userAttributes);
        request.setUserIdentities(identities);

        testExtension.updateUser(request);

        ArgumentCaptor<UserUpdateRequest> argument = ArgumentCaptor.forClass(UserUpdateRequest.class);
        ArgumentCaptor<String> apiArg = ArgumentCaptor.forClass(String.class);
        Mockito.verify(testExtension.iterableService).userUpdate(apiArg.capture(), argument.capture());
        assertEquals(TEST_API_KEY, apiArg.getValue());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(argument.getValue().dataFields.get("some attribute key"), "some attribute value");
    }

    @Test
    public void testReservedAttributeConversion() throws IOException {
        testExtension.iterableService = iterableServiceMock;
        Mockito.when(iterableServiceMock.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(callMock.execute()).thenReturn(testSuccessResponse);

        EventProcessingRequest request = createEventProcessingRequest();
        Map<String, String> userAttributes = new HashMap<String, String>();
        userAttributes.put("some attribute key", "some attribute value");
        userAttributes.put(MPARTICLE_RESERVED_PHONE_ATTR, "+1 (555) 876-5309");
        request.setUserAttributes(userAttributes);
        request.setUserIdentities(userIdentitiesWithEmail);

        testExtension.updateUser(request);
        ArgumentCaptor<UserUpdateRequest> args = ArgumentCaptor.forClass(UserUpdateRequest.class);
        Mockito.verify(testExtension.iterableService, times(1))
                .userUpdate(any(), args.capture());

        assertEquals("Reserved phone number attribute should be converted with non-digit characters removed",
                "+15558765309", args.getValue().dataFields.get("phoneNumber"));
        assertEquals("Non-reserved attributes should be unchanged",
                "some attribute value", args.getValue().dataFields.get("some attribute key"));
        assertNull("mParticle reserved attribute shouldn't be present",
                args.getValue().dataFields.get(MPARTICLE_RESERVED_PHONE_ATTR));
    }

    @Test
    public void testUserAttributeTypeConversion() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);

        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);

        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        EventProcessingRequest request = createEventProcessingRequest();

        List<UserIdentity> identities = new LinkedList<>();
        identities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        request.setUserIdentities(identities);

        Map<String, String> userAttributes = new HashMap<>();
        userAttributes.put("test_bool", "True");
        userAttributes.put("test_int", "123");
        userAttributes.put("test_float", "1.5");
        request.setUserAttributes(userAttributes);

        request.getAccount().getAccountSettings().put(SETTING_COERCE_STRINGS_TO_SCALARS, "True");
        testExtension.updateUser(request);

        request.getAccount().getAccountSettings().put(SETTING_COERCE_STRINGS_TO_SCALARS, "False");
        testExtension.updateUser(request);

        request.getAccount().getAccountSettings().remove(SETTING_COERCE_STRINGS_TO_SCALARS);
        testExtension.updateUser(request);

        ArgumentCaptor<UserUpdateRequest> argument = ArgumentCaptor.forClass(UserUpdateRequest.class);
        ArgumentCaptor<String> apiArg = ArgumentCaptor.forClass(String.class);
        Mockito.verify(testExtension.iterableService, times(3)).userUpdate(apiArg.capture(), argument.capture());

        List<UserUpdateRequest> actualRequests = argument.getAllValues();

        // SETTING_COERCE_STRINGS_TO_SCALARS == True
        assertEquals(true, actualRequests.get(0).dataFields.get("test_bool"));
        assertEquals(123, actualRequests.get(0).dataFields.get("test_int"));
        assertEquals(1.5, actualRequests.get(0).dataFields.get("test_float"));

        // SETTING_COERCE_STRINGS_TO_SCALARS == False
        assertEquals("True", actualRequests.get(1).dataFields.get("test_bool"));
        assertEquals("123", actualRequests.get(1).dataFields.get("test_int"));
        assertEquals("1.5", actualRequests.get(1).dataFields.get("test_float"));

        // SETTING_COERCE_STRINGS_TO_SCALARS not set
        assertEquals("True", actualRequests.get(2).dataFields.get("test_bool"));
        assertEquals("123", actualRequests.get(2).dataFields.get("test_int"));
        assertEquals("1.5", actualRequests.get(2).dataFields.get("test_float"));
    }

    @Test
    public void testProcessUserAttributeChangeEvent() throws Exception {
        //just verify that we're not processing anything - it's all done in processEventProcessingRequest
        testExtension.iterableService = Mockito.mock(IterableService.class);
        testExtension.processUserAttributeChangeEvent(new UserAttributeChangeEvent());
        Mockito.verify(testExtension.iterableService, never()).userUpdate(Mockito.any(), Mockito.any());
    }

    @Test
    public void testProcessCustomEvent() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = createCallMockWithSuccessResponse();
        Mockito.when(testExtension.iterableService.track(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);

        long timeStamp = System.currentTimeMillis();
        CustomEvent event = new CustomEvent();
        event.setTimestamp(timeStamp);
        event.setName("My Event Name");
        EventProcessingRequest request = createEventProcessingRequest();
        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        request.setUserIdentities(userIdentities);
        event.setRequest(request);
        Map<String, String> attributes = new HashMap<>();
        attributes.put("some attribute key", "some attribute value");
        event.setAttributes(attributes);

        testExtension.processCustomEvent(event);

        ArgumentCaptor<TrackRequest> argument = ArgumentCaptor.forClass(TrackRequest.class);
        Mockito.verify(testExtension.iterableService).track(Mockito.any(), argument.capture());
        assertEquals("My Event Name", argument.getValue().getEventName());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals("some attribute value", argument.getValue().dataFields.get("some attribute key"));
        assertEquals((int) (timeStamp / 1000.0), argument.getValue().createdAt + 0);
        assertEquals(event.getId().toString(), argument.getValue().id);
    }

    @Test
    public void testProcessAndroidPushMessageReceiptEvent() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.trackPushOpen(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(new LinkedList<>());
        PushMessageReceiptEvent event = new PushMessageReceiptEvent();
        event.setRequest(eventProcessingRequest);
        IOException exception = null;
        event.setPayload("anything to get past null check");
        // This event won't be processed due to missing email/customerid;
        testExtension.processPushMessageReceiptEvent(event);
        Mockito.verifyZeroInteractions(testExtension.iterableService);

        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        event.setRequest(eventProcessingRequest);
        event.setPayload("{\"google.sent_time\":1507657706679,\"body\":\"example\",\"from\":\"674988899928\",\"itbl\":\"{\\\"campaignId\\\":12345,\\\"isGhostPush\\\":false,\\\"messageId\\\":\\\"1dce4e505b11111ca1111d6fdd774fbd\\\",\\\"templateId\\\":54321}\",\"google.message_id\":\"0:1507657706689231%62399b94f9fd7ecd\"}");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        testExtension.processPushMessageReceiptEvent(event);

        ArgumentCaptor<TrackPushOpenRequest> argument = ArgumentCaptor.forClass(TrackPushOpenRequest.class);
        Mockito.verify(testExtension.iterableService).trackPushOpen(Mockito.any(), argument.capture());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(12345, argument.getValue().campaignId + 0);
        assertEquals(54321, argument.getValue().templateId + 0);
        assertEquals("1dce4e505b11111ca1111d6fdd774fbd", argument.getValue().messageId);
    }

    /**
     * Verify that Android push message receipt event is not handled when campaignId is missing
     */
    @Test
    public void testProcessAndroidPushMessageReceiptWithoutCampaignId() throws IOException {
        testExtension.iterableService = iterableServiceMock;
        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(userIdentitiesWithEmailAndCustomerId);
        eventProcessingRequest.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        PushMessageReceiptEvent event = new PushMessageReceiptEvent();
        event.setRequest(eventProcessingRequest);
        event.setPayload("{\"google.sent_time\":1507657706679,\"body\":\"example\",\"from\":\"674988899928\",\"itbl\":\"{\\\"isGhostPush\\\":false,\\\"messageId\\\":\\\"1dce4e505b11111ca1111d6fdd774fbd\\\",\\\"templateId\\\":54321}\",\"google.message_id\":\"0:1507657706689231%62399b94f9fd7ecd\"}");
        event.setTimestamp(System.currentTimeMillis());

        testExtension.processPushMessageReceiptEvent(event);
        Mockito.verify(iterableServiceMock, never()).trackPushOpen(Mockito.any(), Mockito.any());
    }

    @Test
    public void testProcessiOSPushMessageReceiptEvent() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.trackPushOpen(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(new LinkedList<>());
        PushMessageReceiptEvent event = new PushMessageReceiptEvent();
        event.setRequest(eventProcessingRequest);
        IOException exception = null;
        event.setPayload("anything to get past null check");

        // This event won't be processed due to missing email/customerid;
        testExtension.processPushMessageReceiptEvent(event);
        Mockito.verifyZeroInteractions(testExtension.iterableService);


        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new IosRuntimeEnvironment());
        event.setRequest(eventProcessingRequest);

        event.setPayload("{\"aps\":{\"content-available\":1 }, \"data\":{\"route\":\"example\", \"tag\":\"example\", \"body\":\"example\"}, \"route\":\"example\", \"type\":\"marketing\", \"itbl\":{\"campaignId\":12345, \"messageId\":\"1dce4e505b11111ca1111d6fdd774fbd\", \"templateId\":54321, \"isGhostPush\":false } }");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        testExtension.processPushMessageReceiptEvent(event);

        ArgumentCaptor<TrackPushOpenRequest> argument = ArgumentCaptor.forClass(TrackPushOpenRequest.class);
        Mockito.verify(testExtension.iterableService).trackPushOpen(Mockito.any(), argument.capture());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(12345, argument.getValue().campaignId + 0);
        assertEquals(54321, argument.getValue().templateId + 0);
        assertEquals("1dce4e505b11111ca1111d6fdd774fbd", argument.getValue().messageId);
    }

    /**
     * Verify that iOS push message receipt event is not handled when campaignId is missing
     */
    @Test
    public void testProcessiOSPushMessageReceiptWithoutCampaignId() throws IOException {
        testExtension.iterableService = iterableServiceMock;
        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(userIdentitiesWithEmailAndCustomerId);
        eventProcessingRequest.setRuntimeEnvironment(new IosRuntimeEnvironment());
        PushMessageReceiptEvent event = new PushMessageReceiptEvent();
        event.setRequest(eventProcessingRequest);
        event.setPayload("{\"aps\":{\"content-available\":1 }, \"data\":{\"route\":\"example\", \"tag\":\"example\", \"body\":\"example\"}, \"route\":\"example\", \"type\":\"marketing\", \"itbl\":{\"messageId\":\"1dce4e505b11111ca1111d6fdd774fbd\", \"templateId\":54321, \"isGhostPush\":false } }");
        event.setTimestamp(System.currentTimeMillis());

        testExtension.processPushMessageReceiptEvent(event);
        Mockito.verify(iterableServiceMock, never()).trackPushOpen(Mockito.any(), Mockito.any());
    }

    /**
     * Verify that push message receipt event is not handled when Iterable SDK is installed
     */
    @Test
    public void testProcessPushMessageReceiptEventWithSDK() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);

        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        Map<String, String> integrationAttributes = new HashMap<>();
        integrationAttributes.put("Iterable.sdkVersion", "3.2.1");
        eventProcessingRequest.setIntegrationAttributes(integrationAttributes);
        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        PushMessageReceiptEvent event = new PushMessageReceiptEvent();
        event.setRequest(eventProcessingRequest);
        event.setPayload("{\"google.sent_time\":1507657706679,\"body\":\"example\",\"from\":\"674988899928\",\"itbl\":\"{\\\"campaignId\\\":12345,\\\"isGhostPush\\\":false,\\\"messageId\\\":\\\"1dce4e505b11111ca1111d6fdd774fbd\\\",\\\"templateId\\\":54321}\",\"google.message_id\":\"0:1507657706689231%62399b94f9fd7ecd\"}");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        testExtension.processPushMessageReceiptEvent(event);

        Mockito.verify(testExtension.iterableService, never()).trackPushOpen(Mockito.any(), Mockito.any());
    }

    /**
     * This test creates 3 audiences and 2 users.
     * <p>
     * User 1: Two audiences added, 1 removed
     * User 2: 1 audience removed, 2 added
     * <p>
     * It then verifies that subscribe/unsubcribe are called the correct amount and with the right list ids
     */
    @Test
    public void testProcessAudienceMembershipChangeRequest() throws Exception {
        IterableService service = Mockito.mock(IterableService.class);
        testExtension.iterableService = service;
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(service.listSubscribe(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(service.listUnsubscribe(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        ListResponse apiResponse = new ListResponse();
        Response<ListResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        Audience audience = new Audience();
        Map<String, String> audienceSubscriptionSettings = new HashMap<>();
        audienceSubscriptionSettings.put(IterableExtension.SETTING_LIST_ID, "1");
        audience.setAudienceSubscriptionSettings(audienceSubscriptionSettings);
        audience.setAudienceAction(Audience.AudienceAction.ADD);

        Audience audience2 = new Audience();
        Map<String, String> audienceSubscriptionSettings2 = new HashMap<>();
        audienceSubscriptionSettings2.put(IterableExtension.SETTING_LIST_ID, "2");
        audience2.setAudienceSubscriptionSettings(audienceSubscriptionSettings2);
        audience2.setAudienceAction(Audience.AudienceAction.ADD);

        Audience audience3 = new Audience();
        Map<String, String> audienceSubscriptionSettings3 = new HashMap<>();
        audienceSubscriptionSettings3.put(IterableExtension.SETTING_LIST_ID, "3");
        audience3.setAudienceSubscriptionSettings(audienceSubscriptionSettings3);
        audience3.setAudienceAction(Audience.AudienceAction.DELETE);

        List<Audience> list1 = new LinkedList<>();
        list1.add(audience);
        list1.add(audience2);
        list1.add(audience3);

        Audience audience4 = new Audience();
        Map<String, String> audienceSubscriptionSettings4 = new HashMap<>();
        audienceSubscriptionSettings4.put(IterableExtension.SETTING_LIST_ID, "1");
        audience4.setAudienceSubscriptionSettings(audienceSubscriptionSettings4);
        audience4.setAudienceAction(Audience.AudienceAction.DELETE);

        Audience audience5 = new Audience();
        Map<String, String> audienceSubscriptionSettings5 = new HashMap<>();
        audienceSubscriptionSettings5.put(IterableExtension.SETTING_LIST_ID, "2");
        audience5.setAudienceSubscriptionSettings(audienceSubscriptionSettings5);
        audience5.setAudienceAction(Audience.AudienceAction.DELETE);

        Audience audience6 = new Audience();
        Map<String, String> audienceSubscriptionSettings6 = new HashMap<>();
        audienceSubscriptionSettings6.put(IterableExtension.SETTING_LIST_ID, "3");
        audience6.setAudienceSubscriptionSettings(audienceSubscriptionSettings6);
        audience6.setAudienceAction(Audience.AudienceAction.ADD);

        List<Audience> list2 = new LinkedList<>();
        list2.add(audience4);
        list2.add(audience5);
        list2.add(audience6);

        List<UserProfile> profiles = new LinkedList<>();
        UserProfile profile1 = new UserProfile();
        profile1.setAudiences(list1);
        List<UserIdentity> userIdentities1 = new LinkedList<>();
        userIdentities1.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities1.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        profile1.setUserIdentities(userIdentities1);
        profiles.add(profile1);

        UserProfile profile2 = new UserProfile();
        profile2.setAudiences(list2);
        List<UserIdentity> userIdentities2 = new LinkedList<>();
        userIdentities2.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest-2@mparticle.com"));
        userIdentities2.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "1234567"));
        profile2.setUserIdentities(userIdentities2);
        profiles.add(profile2);

        AudienceMembershipChangeRequest request = new AudienceMembershipChangeRequest();
        Account account = new Account();
        Map<String, String> settings = new HashMap<>();
        settings.put(SETTING_API_KEY, "some api key");
        account.setAccountSettings(settings);
        request.setAccount(account);
        request.setUserProfiles(profiles);

        testExtension.processAudienceMembershipChangeRequest(request);

        ArgumentCaptor<SubscribeRequest> argument = ArgumentCaptor.forClass(SubscribeRequest.class);
        ArgumentCaptor<String> apiArgument = ArgumentCaptor.forClass(String.class);
        Mockito.verify(service, Mockito.times(3)).listSubscribe(apiArgument.capture(), argument.capture());
        String apiKey = apiArgument.getValue();
        assertEquals("some api key", apiKey);
        List<SubscribeRequest> subscribeRequests = argument.getAllValues();
        int i = 0;
        for (SubscribeRequest subscribeRequest : subscribeRequests) {
            switch (subscribeRequest.listId) {
                case 1:
                    assertEquals(subscribeRequest.subscribers.get(0).email, "mptest@mparticle.com");
                    i++;
                    break;
                case 2:
                    assertEquals(subscribeRequest.subscribers.get(0).email, "mptest@mparticle.com");
                    i++;
                    break;
                case 3:
                    assertEquals(subscribeRequest.subscribers.get(0).email, "mptest-2@mparticle.com");
                    i++;
                    break;
            }
        }
        assertEquals(3, i);

        ArgumentCaptor<UnsubscribeRequest> unsubArg = ArgumentCaptor.forClass(UnsubscribeRequest.class);
        Mockito.verify(service, Mockito.times(3)).listUnsubscribe(Mockito.any(), unsubArg.capture());
        List<UnsubscribeRequest> unsubscribeRequests = unsubArg.getAllValues();
        i = 0;
        for (UnsubscribeRequest unsubscribeRequest : unsubscribeRequests) {
            switch (unsubscribeRequest.listId) {
                case 1:
                    assertEquals(unsubscribeRequest.subscribers.get(0).email, "mptest-2@mparticle.com");
                    i++;
                    break;
                case 2:
                    assertEquals(unsubscribeRequest.subscribers.get(0).email, "mptest-2@mparticle.com");
                    i++;
                    break;
                case 3:
                    assertEquals(unsubscribeRequest.subscribers.get(0).email, "mptest@mparticle.com");
                    i++;
                    break;
            }
        }
        assertEquals(3, i);
    }

    @Test
    public void testProcessAudienceMembershipChangeWithMPID() throws IOException {
        testExtension.iterableService = iterableServiceMock;
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(iterableServiceMock.listSubscribe(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(iterableServiceMock.listUnsubscribe(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        ListResponse apiResponse = new ListResponse();
        Response<ListResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        List userProfileList = new LinkedList<UserProfile>();
        testUserProfile.setMpId("m1");
        testUserProfile.setAudiences(testAudienceList);
        testUserProfileWithEmail.setMpId("m2");
        testUserProfileWithEmail.setAudiences(testAudienceList);
        testUserProfileWithEmailAndCustomerId.setMpId("m3");
        testUserProfileWithEmailAndCustomerId.setAudiences(testAudienceList);
        userProfileList.add(testUserProfile);
        userProfileList.add(testUserProfileWithEmail);
        userProfileList.add(testUserProfileWithEmailAndCustomerId);

        AudienceMembershipChangeRequest request = new AudienceMembershipChangeRequest();
        testAccount.getAccountSettings().put(SETTING_USER_ID_FIELD, USER_ID_FIELD_MPID);
        request.setAccount(testAccount);
        request.setUserProfiles(userProfileList);

        testExtension.processAudienceMembershipChangeRequest(request);

        ArgumentCaptor<SubscribeRequest> subscribeArgs = ArgumentCaptor.forClass(SubscribeRequest.class);
        List<SubscribeRequest> subscribeRequests = subscribeArgs.getAllValues();
        Mockito.verify(iterableServiceMock, Mockito.times(2)).listSubscribe(Mockito.any(), subscribeArgs.capture());
        Collections.sort(
                subscribeRequests,
                (a, b) -> a.listId > b.listId ? 1 : a.listId == b.listId ? 0 : -1
        );
        assertEquals(1, subscribeRequests.get(0).listId.intValue());
        assertEquals(2, subscribeRequests.get(1).listId.intValue());
        int expectedUserSubscribeCount = 0;
        for (ApiUser user : subscribeRequests.get(0).subscribers) {
            switch (user.email) {
                case "m1@placeholder.email":
                    // testUserProfile
                    assertEquals("m1", user.userId);
                    expectedUserSubscribeCount++;
                    break;
                case "email_only@iterable.com":
                    // testUserProfileWithEmail
                    assertEquals("m2", user.userId);
                    expectedUserSubscribeCount++;
                    break;
                case "email_and_id@iterable.com":
                    // testUserProfileWIthEmailAndCustomerId
                    assertEquals("m3", user.userId);
                    expectedUserSubscribeCount++;
                    break;
            }
        }
        assertEquals(3, expectedUserSubscribeCount);

        ArgumentCaptor<UnsubscribeRequest> unsubArg = ArgumentCaptor.forClass(UnsubscribeRequest.class);
        List<UnsubscribeRequest> unsubscribeRequests = unsubArg.getAllValues();
        Mockito.verify(iterableServiceMock, Mockito.times(1)).listUnsubscribe(Mockito.any(), unsubArg.capture());
        assertEquals(3, unsubscribeRequests.get(0).listId.intValue());
        int expectedUserUnsubscribeCount = 0;
        for (ApiUser user : unsubscribeRequests.get(0).subscribers) {
            switch (user.email) {
                case "m1@placeholder.email":
                    // testUserProfile
                    assertEquals("m1", user.userId);
                    expectedUserUnsubscribeCount++;
                    break;
                case "email_only@iterable.com":
                    // testUserProfileWithEmail
                    assertEquals("m2", user.userId);
                    expectedUserUnsubscribeCount++;
                    break;
                case "email_and_id@iterable.com":
                    // testUserProfileWIthEmailAndCustomerId
                    assertEquals("m3", user.userId);
                    expectedUserUnsubscribeCount++;
                    break;
            }
        }
        assertEquals(3, expectedUserUnsubscribeCount);
    }

    @Test
    public void testConvertToCommerceItem() throws Exception {
        Product product = new Product();
        product.setId("some id");
        product.setName("some name");
        product.setCategory("some category");
        Map<String, String> attributes = new HashMap<>();
        attributes.put("a key", "a value");
        attributes.put("an int key", "123");
        product.setAttributes(attributes);
        product.setQuantity(new BigDecimal(1.4));
        CommerceItem item = testExtension.convertToCommerceItem(product, true);
        assertEquals("some id", item.id);
        assertEquals("some id", item.sku);
        assertEquals("some name", item.name);
        assertEquals("some category", item.categories.get(0));
        assertEquals("a value", item.dataFields.get("a key"));
        assertEquals(123, item.dataFields.get("an int key"));
        assertEquals((Integer) new BigDecimal(1.4).intValue(), item.quantity);

        item = testExtension.convertToCommerceItem(product, false);
        assertEquals("123", item.dataFields.get("an int key"));


    }

    @Test
    public void testProcessProductActionEvent() throws Exception {
        ProductActionEvent event = new ProductActionEvent();
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.trackPurchase(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        long timeStamp = System.currentTimeMillis();

        event.setTimestamp(timeStamp);

        EventProcessingRequest request = createEventProcessingRequest();
        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        request.setUserIdentities(userIdentities);
        event.setRequest(request);
        event.setTotalAmount(new BigDecimal(101d));
        List<Product> products = new LinkedList<>();
        Product product1 = new Product();
        product1.setId("product_id_1");
        Product product2 = new Product();
        product2.setId("product_id_2");
        products.add(product1);
        products.add(product2);
        event.setProducts(products);

        for (ProductActionEvent.Action action : ProductActionEvent.Action.values()) {
            if (action != ProductActionEvent.Action.PURCHASE) {
                event.setAction(action);
                testExtension.processProductActionEvent(event);
                Mockito.verifyZeroInteractions(testExtension.iterableService);
            }
        }

        event.setAction(ProductActionEvent.Action.PURCHASE);
        testExtension.processProductActionEvent(event);
        ArgumentCaptor<TrackPurchaseRequest> purchaseArgs = ArgumentCaptor.forClass(TrackPurchaseRequest.class);
        Mockito.verify(testExtension.iterableService, Mockito.times(1)).trackPurchase(Mockito.any(), purchaseArgs.capture());
        TrackPurchaseRequest trackPurchaseRequest = purchaseArgs.getValue();
        assertEquals(trackPurchaseRequest.user.email, "mptest@mparticle.com");
        assertEquals(trackPurchaseRequest.user.userId, "123456");
        assertEquals(trackPurchaseRequest.items.size(), 2);
        assertEquals(trackPurchaseRequest.total, new BigDecimal(101d));
        assertEquals(trackPurchaseRequest.id, event.getId().toString());
    }

    @Test
    public void testGetPlaceholderEmailNoEnvironmentOrStamp() {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(null);
        request.setDeviceApplicationStamp(null);
        Exception e = null;
        try {
            String email = testExtension.getPlaceholderEmail(request);
        }catch (IOException ioe) {
            e = ioe;
        }
        assertNotNull(e);
    }

    @Test
    public void testGetPlaceholderEmailNoEnvironment() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(null);
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("1234@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentButNoIds() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("1234@placeholder.email", email);

        request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new IosRuntimeEnvironment());
        request.setDeviceApplicationStamp("12345");
        email = testExtension.getPlaceholderEmail(request);
        assertEquals("12345@placeholder.email", email);

        request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        request.setDeviceApplicationStamp("123456");
        email = testExtension.getPlaceholderEmail(request);
        assertEquals("123456@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentIDFA() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new IosRuntimeEnvironment());
        DeviceIdentity idfa = new DeviceIdentity(DeviceIdentity.Type.IOS_ADVERTISING_ID, Identity.Encoding.RAW, "foo-idfa");
        ((IosRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa));
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfa@placeholder.email", email);

        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        ((TVOSRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa));
        request.setDeviceApplicationStamp("1234");
        email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfa@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentIDFV() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new IosRuntimeEnvironment());
        DeviceIdentity idfv = new DeviceIdentity(DeviceIdentity.Type.IOS_VENDOR_ID, Identity.Encoding.RAW, "foo-idfv");
        DeviceIdentity idfa = new DeviceIdentity(DeviceIdentity.Type.IOS_ADVERTISING_ID, Identity.Encoding.RAW, "foo-idfa");
        ((IosRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa, idfv));
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfv@placeholder.email", email);

        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        ((TVOSRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa, idfv));
        request.setDeviceApplicationStamp("1234");
        email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfv@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentGAID() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        DeviceIdentity idfv = new DeviceIdentity(DeviceIdentity.Type.ANDROID_ID, Identity.Encoding.RAW, "foo-aid");
        DeviceIdentity idfa = new DeviceIdentity(DeviceIdentity.Type.GOOGLE_ADVERTISING_ID, Identity.Encoding.RAW, "foo-gaid");
        ((AndroidRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa, idfv));
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-gaid@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentAndroidID() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        DeviceIdentity idfv = new DeviceIdentity(DeviceIdentity.Type.ANDROID_ID, Identity.Encoding.RAW, "foo-aid");
        ((AndroidRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfv));
        request.setDeviceApplicationStamp("1234");
        String email = testExtension.getPlaceholderEmail(request);
        assertEquals("foo-aid@placeholder.email", email);
    }

    @Test
    public void testUpdateSubscriptionsEvent() throws Exception {
        testExtension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(testExtension.iterableService.updateSubscriptions(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(testExtension.iterableService.track(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        long timeStamp = System.currentTimeMillis();
        CustomEvent event = new CustomEvent();
        event.setTimestamp(timeStamp);
        event.setName(IterableExtension.UPDATE_SUBSCRIPTIONS_CUSTOM_EVENT_NAME);
        EventProcessingRequest request = createEventProcessingRequest();
        Map<String, String> settings = request.getAccount().getAccountSettings();
        settings.put(SETTING_API_KEY, "foo api key 2");
        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        request.setUserIdentities(userIdentities);
        event.setRequest(request);
        Map<String, String> attributes = new HashMap<>();
        // Added some random spaces to test it doesn't mess with parsing
        attributes.put(IterableExtension.EMAIL_LIST_ID_LIST_KEY, "1, 2,  3, 4 , 5 , 6 , 7  ,8");
        attributes.put(IterableExtension.UNSUBSCRIBE_CHANNEL_ID_LIST_KEY, " 1, 3, 5   ,7 ");
        attributes.put(IterableExtension.UNSUBSCRIBE_MESSAGE_TYPE_ID_LIST_KEY, " 1, 3, 5   ,7 ,10");
        attributes.put(IterableExtension.CAMPAIGN_ID_KEY, "2323");
        attributes.put(IterableExtension.TEMPLATE_ID_KEY, " 5555 ");
        event.setAttributes(attributes);
        List<Integer> expectedEmailListIdList = Arrays.asList(1,2,3,4,5,6,7,8);
        List<Integer> expectedChannelIdList = Arrays.asList(1,3,5,7);
        List<Integer> expectedMessageTypeIdList = Arrays.asList(1,3,5,7,10);
        int expectedCampaignId = 2323;
        int expectedTemplateId = 5555;

        testExtension.processCustomEvent(event);

        ArgumentCaptor<UpdateSubscriptionsRequest> argument = ArgumentCaptor.forClass(UpdateSubscriptionsRequest.class);
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(testExtension.iterableService).updateSubscriptions(stringArgumentCaptor.capture(), argument.capture());
        assertEquals("foo api key 2", stringArgumentCaptor.getValue());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals(expectedEmailListIdList, argument.getValue().emailListIds);
        assertEquals(expectedChannelIdList, argument.getValue().unsubscribedChannelIds);
        assertEquals(expectedMessageTypeIdList, argument.getValue().unsubscribedMessageTypeIds);
        assertEquals(expectedCampaignId, (int)argument.getValue().campaignId);
        assertEquals(expectedTemplateId, (int)argument.getValue().templateId);
    }

    @Test
    public void testHandleIterableResponseSuccess() throws RetriableError {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        testExtension.handleIterableResponse(call, testSuccessResponse,
                UUID.fromString("d0567916-c2c7-11ea-b3de-0242ac130004"));
        assertEquals("A success response shouldn't log", "", outContent.toString());
        System.setOut(System.out);
    }

    @Test
    public void testHandleIterableResponseLogsRetriableError() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        Response errorResponse = createErrorResponse(429, "test");

        try {
            testExtension.handleIterableResponse(call, errorResponse,
                    UUID.fromString("d0567916-c2c7-11ea-b3de-0242ac130004"));
        } catch (RetriableError e) {
            // ignored
        } finally {
            Map<String, String> logMessage = gson.fromJson(outContent.toString(), Map.class);
            assertEquals("RetriableError", logMessage.get("errorType") );
            System.setOut(System.out);
        }
    }

    @Test
    public void testHandleIterableResponseLogsNonRetriableError() throws RetriableError {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        Response errorResponse = createErrorResponse(400, "test");
        testExtension.handleIterableResponse(call, errorResponse,
                UUID.fromString("d0567916-c2c7-11ea-b3de-0242ac130004"));
        Map<String, String> logMessage = gson.fromJson(outContent.toString(), Map.class);
        assertEquals("NonRetriableError", logMessage.get("errorType") );
        System.setOut(System.out);
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableResponseWith429() throws RetriableError {
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        Response itbl492 = Response.error(429, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableResponse(call, itbl492, UUID.randomUUID());
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableResponseWith502() throws RetriableError {
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        Response itbl502 = Response.error(502, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableResponse(call, itbl502, UUID.randomUUID());
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableResponseWith504() throws RetriableError {
        Call<IterableApiResponse> call = testExtension.iterableService.track("foo", new TrackRequest());
        Response itbl504 = Response.error(504, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableResponse(call, itbl504, UUID.randomUUID());
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableListResponseWith429() throws RetriableError {
        Call<ListResponse> call = testExtension.iterableService.listSubscribe("foo", new SubscribeRequest());
        Response itbl492 = Response.error(429, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableListResponse(call, itbl492, UUID.randomUUID());
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableListResponseWith502() throws RetriableError {
        Call<ListResponse> call = testExtension.iterableService.listSubscribe("foo", new SubscribeRequest());
        Response itbl502 = Response.error(502, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableListResponse(call, itbl502, UUID.randomUUID());
    }

    @Test(expected = RetriableError.class)
    public void testHandleIterableListResponseWith504() throws RetriableError {
        Call<ListResponse> call = testExtension.iterableService.listSubscribe("foo", new SubscribeRequest());
        Response itbl504 = Response.error(504, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), "{}"));
        testExtension.handleIterableListResponse(call, itbl504, UUID.randomUUID());
    }

    @Test
    public void testMakeIterableRequestWithSuccess() throws IOException {
        Call call = createCallMockWithSuccessResponse();
        testExtension.makeIterableRequest(call, UUID.randomUUID());
        Mockito.verify(call).execute();
    }

    @Test
    public void testMakeIterableRequestWithListSuccess() throws IOException {
        Call call = Mockito.mock(Call.class);
        ListResponse listResponse = new ListResponse();
        Response<ListResponse> response = Response.success(listResponse);
        Mockito.when(call.execute()).thenReturn(response);

        testExtension.makeIterableRequest(call, UUID.randomUUID());
        Mockito.verify(call).execute();
    }

    @Test(expected = RetriableError.class)
    public void testMakeIterableRequestWithTimeoutThrowsRetriableError() throws IOException {
        Call call = Mockito.mock(Call.class);
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.iterable.com")
                .addEncodedPathSegment("/api/events/track")
                .build();
        Request request = new Request.Builder()
                .method("POST", RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{}"))
                .url(url)
                .build();
        Mockito.when(call.request()).thenReturn(request);
        Mockito.when(call.execute()).thenThrow(java.net.SocketTimeoutException.class);

        testExtension.makeIterableRequest(call, UUID.randomUUID());
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

    private Call createCallMockWithSuccessResponse() throws IOException {
        Call callMock = Mockito.mock(Call.class);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        return callMock;
    }

    private Response createErrorResponse(int httpCode, String apiCode) {
        return Response.error(httpCode, ResponseBody.create(
                MediaType.parse("application/json; charset=utf-8"), String.format("{code:\"%s\"}", apiCode)));
    }

    @Test
    public void tempTest() {
        IterableExtensionLogger logger = new IterableExtensionLogger("hi");
        try {
            throw new ArithmeticException("divided by 0");
        } catch (ArithmeticException e ) {
            System.out.println("error");
            logger.logUnexpectedError(e);
        }
    }
}


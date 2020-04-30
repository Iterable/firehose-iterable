package com.mparticle.ext.iterable;

import com.mparticle.iterable.*;
import com.mparticle.sdk.model.audienceprocessing.Audience;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.UserProfile;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.Account;
import com.mparticle.sdk.model.registration.ModuleRegistrationResponse;
import com.mparticle.sdk.model.registration.Setting;
import com.mparticle.sdk.model.registration.UserIdentityPermission;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.mparticle.ext.iterable.IterableExtension.SETTING_API_KEY;
import static com.mparticle.ext.iterable.IterableExtension.SETTING_COERCE_STRINGS_TO_SCALARS;
import static com.mparticle.ext.iterable.IterableExtension.SETTING_USER_ID_FIELD;
import static org.junit.Assert.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class IterableExtensionTest {
    private static final String TEST_API_KEY = "foo api key";

    @org.junit.Test
    public void testProcessEventProcessingRequest() throws Exception {
        IterableExtension extension = new IterableExtension();
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
        extension.processEventProcessingRequest(request);
        assertNotNull("IterableService should have been created", extension.iterableService);

        assertEquals("Events should have been in order",1, request.getEvents().get(0).getTimestamp());
        assertEquals("Events should have been in order",2, request.getEvents().get(1).getTimestamp());
        assertEquals("Events should have been in order",3, request.getEvents().get(2).getTimestamp());
        assertEquals("Events should have been in order",4, request.getEvents().get(3).getTimestamp());
    }

    @org.junit.Test
    public void testUpdateUser() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        EventProcessingRequest request = createEventProcessingRequest();
        //no user identities, no API call
        extension.updateUser(request);
        UserUpdateRequest userUpdateRequest = new UserUpdateRequest();
        Mockito.verify(extension.iterableService, never()).userUpdate(TEST_API_KEY, userUpdateRequest);

        //user identities but no email/userid, no API call
        List<UserIdentity> identities = new LinkedList<>();
        identities.add(new UserIdentity(UserIdentity.Type.FACEBOOK, Identity.Encoding.RAW, "123456"));
        request.setUserIdentities(identities);
        extension.updateUser(request);
        Mockito.verify(extension.iterableService, never()).userUpdate(TEST_API_KEY, userUpdateRequest);

        //ok, now we should get a single API call
        identities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        identities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        Map<String, String> userAttributes = new HashMap<String, String>();
        userAttributes.put("some attribute key", "some attribute value");
        request.setUserAttributes(userAttributes);
        request.setUserIdentities(identities);

        extension.updateUser(request);

        ArgumentCaptor<UserUpdateRequest> argument = ArgumentCaptor.forClass(UserUpdateRequest.class);
        ArgumentCaptor<String> apiArg = ArgumentCaptor.forClass(String.class);
        Mockito.verify(extension.iterableService).userUpdate(apiArg.capture(), argument.capture());
        assertEquals(TEST_API_KEY, apiArg.getValue());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(argument.getValue().dataFields.get("some attribute key"), "some attribute value");

        apiResponse.code = "anything but success";

        IOException exception = null;
        try {
            extension.updateUser(request);
        } catch (IOException ioe) {
            exception = ioe;
        }
        assertNotNull("Iterable extension should have thrown an IOException", exception);

    }

    @org.junit.Test
    public void testUserAttributeTypeConversion() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);

        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
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
        extension.updateUser(request);

        request.getAccount().getAccountSettings().put(SETTING_COERCE_STRINGS_TO_SCALARS, "False");
        extension.updateUser(request);

        request.getAccount().getAccountSettings().remove(SETTING_COERCE_STRINGS_TO_SCALARS);
        extension.updateUser(request);

        ArgumentCaptor<UserUpdateRequest> argument = ArgumentCaptor.forClass(UserUpdateRequest.class);
        ArgumentCaptor<String> apiArg = ArgumentCaptor.forClass(String.class);
        Mockito.verify(extension.iterableService, times(3)).userUpdate(apiArg.capture(), argument.capture());

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

    @org.junit.Test
    public void testProcessUserAttributeChangeEvent() throws Exception {
        //just verify that we're not processing anything - it's all done in processEventProcessingRequest
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        extension.processUserAttributeChangeEvent(new UserAttributeChangeEvent());
        Mockito.verify(extension.iterableService, never()).userUpdate(Mockito.any(), Mockito.any());
    }

    /**
     * Simple test to make sure Iterable is registering for the proper data points.
     *
     * @throws Exception
     */
    @org.junit.Test
    public void testProcessRegistrationRequest() throws Exception {
        ModuleRegistrationResponse response = new IterableExtension().processRegistrationRequest(null);
        List<UserIdentityPermission> userIdentities = response.getPermissions().getUserIdentities();
        assertEquals(2, userIdentities.size());
        boolean email, customer;
        email = userIdentities.get(0).getType().equals(UserIdentity.Type.EMAIL) ||
                userIdentities.get(1).getType().equals(UserIdentity.Type.EMAIL);

        customer = userIdentities.get(0).getType().equals(UserIdentity.Type.CUSTOMER) ||
                userIdentities.get(1).getType().equals(UserIdentity.Type.CUSTOMER);


        assertTrue("Iterable Extension should register for email permission", email);
        assertTrue("Iterable Extension should register for customer id permission", customer);

        List<Setting> accountSettings = response.getEventProcessingRegistration().getAccountSettings();
        assertTrue("There should be a single text setting (api key) for iterable", accountSettings.get(0).getType().equals(Setting.Type.TEXT));

        List<Event.Type> eventTypes = response.getEventProcessingRegistration().getSupportedEventTypes();
        assertTrue("Iterable should support custom events", eventTypes.contains(Event.Type.CUSTOM_EVENT));
        assertTrue("Iterable should support push subscriptions", eventTypes.contains(Event.Type.PUSH_SUBSCRIPTION));
        assertTrue("Iterable should support user identity changes", eventTypes.contains(Event.Type.USER_IDENTITY_CHANGE));

        Setting setting = response.getAudienceProcessingRegistration().getAudienceConnectionSettings().get(0);
        assertTrue("Iterable audiences should have a single Integer setting", setting.getType().equals(Setting.Type.INTEGER));
    }

    @org.junit.Test
    public void testProcessCustomEvent() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.track(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

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

        extension.processCustomEvent(event);

        ArgumentCaptor<TrackRequest> argument = ArgumentCaptor.forClass(TrackRequest.class);
        Mockito.verify(extension.iterableService).track(Mockito.any(), argument.capture());
        assertEquals("My Event Name", argument.getValue().getEventName());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals("some attribute value", argument.getValue().dataFields.get("some attribute key"));
        assertEquals((int) (timeStamp / 1000.0), argument.getValue().createdAt + 0);

        apiResponse.code = "anything but success";

        IOException exception = null;
        try {
            extension.processCustomEvent(event);
        } catch (IOException ioe) {
            exception = ioe;
        }
        assertNotNull("Iterable extension should have thrown an IOException", exception);
    }

    @org.junit.Test
    public void testProcessAndroidPushOpenEvent() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.trackPushOpen(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(extension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);
        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(new LinkedList<>());
        PushMessageOpenEvent event = new PushMessageOpenEvent();
        event.setRequest(eventProcessingRequest);
        eventProcessingRequest.setEvents(Collections.singletonList(event));
        IOException exception = null;
        event.setPayload("anything to get past null check");
        try {
            extension.processEventProcessingRequest(eventProcessingRequest);
        } catch (IOException ioe) {
            exception = ioe;
        }
        assertNotNull("Iterable should have thrown an exception due to missing email/customerid", exception);

        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        event.setRequest(eventProcessingRequest);
        event.setPayload("{\"google.sent_time\":1507657706679,\"body\":\"example\",\"from\":\"674988899928\",\"itbl\":\"{\\\"campaignId\\\":12345,\\\"isGhostPush\\\":false,\\\"messageId\\\":\\\"1dce4e505b11111ca1111d6fdd774fbd\\\",\\\"templateId\\\":54321}\",\"google.message_id\":\"0:1507657706689231%62399b94f9fd7ecd\"}");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        extension.processEventProcessingRequest(eventProcessingRequest);

        ArgumentCaptor<TrackPushOpenRequest> argument = ArgumentCaptor.forClass(TrackPushOpenRequest.class);
        Mockito.verify(extension.iterableService).trackPushOpen(Mockito.any(), argument.capture());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(12345, argument.getValue().campaignId + 0);
        assertEquals(54321, argument.getValue().templateId + 0);
        assertEquals("1dce4e505b11111ca1111d6fdd774fbd", argument.getValue().messageId);


        apiResponse.code = "anything but success";

        IOException exception2 = null;
        try {
            extension.processEventProcessingRequest(eventProcessingRequest);
        } catch (IOException ioe) {
            exception2 = ioe;
        }
        assertNotNull("Iterable extension should have thrown an IOException", exception2);

    }

    @org.junit.Test
    public void testProcessiOSPushOpenEvent() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.trackPushOpen(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(extension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        eventProcessingRequest.setUserIdentities(new LinkedList<>());
        PushMessageOpenEvent event = new PushMessageOpenEvent();
        event.setRequest(eventProcessingRequest);
        IOException exception = null;
        event.setPayload("anything to get past null check");
        eventProcessingRequest.setEvents(Collections.singletonList(event));
        try {
            extension.processEventProcessingRequest(eventProcessingRequest);
        } catch (IOException ioe) {
            exception = ioe;
        }
        assertNotNull("Iterable should have thrown an exception due to missing email/customerid", exception);

        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new IosRuntimeEnvironment());
        eventProcessingRequest.setEvents(Collections.singletonList(event));
        event.setRequest(eventProcessingRequest);

        event.setPayload("{\"aps\":{\"content-available\":1 }, \"data\":{\"route\":\"example\", \"tag\":\"example\", \"body\":\"example\"}, \"route\":\"example\", \"type\":\"marketing\", \"itbl\":{\"campaignId\":12345, \"messageId\":\"1dce4e505b11111ca1111d6fdd774fbd\", \"templateId\":54321, \"isGhostPush\":false } }");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        extension.processEventProcessingRequest(eventProcessingRequest);

        ArgumentCaptor<TrackPushOpenRequest> argument = ArgumentCaptor.forClass(TrackPushOpenRequest.class);
        Mockito.verify(extension.iterableService).trackPushOpen(Mockito.any(), argument.capture());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals("123456", argument.getValue().userId);
        assertEquals(12345, argument.getValue().campaignId + 0);
        assertEquals(54321, argument.getValue().templateId + 0);
        assertEquals("1dce4e505b11111ca1111d6fdd774fbd", argument.getValue().messageId);


        apiResponse.code = "anything but success";

        IOException exception2 = null;
        try {
            extension.processEventProcessingRequest(eventProcessingRequest);
        } catch (IOException ioe) {
            exception2 = ioe;
        }
        assertNotNull("Iterable extension should have thrown an IOException", exception2);

    }

    /**
     * Verify that push message receipt event is not handled when Iterable SDK is installed
     */
    @Test
    public void testProcessPushOpenEventWithSDK() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.userUpdate(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
        Mockito.when(callMock.execute()).thenReturn(response);

        EventProcessingRequest eventProcessingRequest = createEventProcessingRequest();
        Map<String, String> integrationAttributes = new HashMap<>();
        integrationAttributes.put("Iterable.sdkVersion", "3.2.1");
        eventProcessingRequest.setIntegrationAttributes(integrationAttributes);
        List<UserIdentity> userIdentities = new LinkedList<>();
        userIdentities.add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, "mptest@mparticle.com"));
        userIdentities.add(new UserIdentity(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, "123456"));
        eventProcessingRequest.setUserIdentities(userIdentities);
        eventProcessingRequest.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        PushMessageOpenEvent event = new PushMessageOpenEvent();
        event.setRequest(eventProcessingRequest);
        eventProcessingRequest.setEvents(Collections.singletonList(event));
        event.setPayload("{\"google.sent_time\":1507657706679,\"body\":\"example\",\"from\":\"674988899928\",\"itbl\":\"{\\\"campaignId\\\":12345,\\\"isGhostPush\\\":false,\\\"messageId\\\":\\\"1dce4e505b11111ca1111d6fdd774fbd\\\",\\\"templateId\\\":54321}\",\"google.message_id\":\"0:1507657706689231%62399b94f9fd7ecd\"}");

        long timeStamp = System.currentTimeMillis();
        event.setTimestamp(timeStamp);

        extension.processEventProcessingRequest(eventProcessingRequest);

        Mockito.verify(extension.iterableService, never()).trackPushOpen(Mockito.any(), Mockito.any());
    }

    /**
     * This test creates 3 audiences and 2 users.
     * <p>
     * User 1: Two audiences added, 1 removed
     * User 2: 1 audience removed, 2 added
     * <p>
     * It then verifies that subscribe/unsubcribe are called the correct amount and with the right list ids
     */
    @org.junit.Test
    public void testProcessAudienceMembershipChangeRequest() throws Exception {
        IterableExtension extension = new IterableExtension();
        IterableService service = Mockito.mock(IterableService.class);
        extension.iterableService = service;
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(service.trackPushOpen(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        IterableApiResponse apiResponse = new IterableApiResponse();
        apiResponse.code = IterableApiResponse.SUCCESS_MESSAGE;
        Response<IterableApiResponse> response = Response.success(apiResponse);
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

        extension.processAudienceMembershipChangeRequest(request);

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

    @org.junit.Test
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
        CommerceItem item = new IterableExtension().convertToCommerceItem(product, true);
        assertEquals("some id", item.id);
        assertEquals("some id", item.sku);
        assertEquals("some name", item.name);
        assertEquals("some category", item.categories.get(0));
        assertEquals("a value", item.dataFields.get("a key"));
        assertEquals(123, item.dataFields.get("an int key"));
        assertEquals((Integer) new BigDecimal(1.4).intValue(), item.quantity);

        item = new IterableExtension().convertToCommerceItem(product, false);
        assertEquals("123", item.dataFields.get("an int key"));


    }

    @org.junit.Test
    public void testProcessProductActionEvent() throws Exception {
        ProductActionEvent event = new ProductActionEvent();
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.trackPurchase(Mockito.any(), Mockito.any()))
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
                extension.processProductActionEvent(event);
                Mockito.verifyZeroInteractions(extension.iterableService);
            }
        }

        event.setAction(ProductActionEvent.Action.PURCHASE);
        extension.processProductActionEvent(event);
        ArgumentCaptor<TrackPurchaseRequest> purchaseArgs = ArgumentCaptor.forClass(TrackPurchaseRequest.class);
        Mockito.verify(extension.iterableService, Mockito.times(1)).trackPurchase(Mockito.any(), purchaseArgs.capture());
        TrackPurchaseRequest trackPurchaseRequest = purchaseArgs.getValue();
        assertEquals(trackPurchaseRequest.user.email, "mptest@mparticle.com");
        assertEquals(trackPurchaseRequest.user.userId, "123456");
        assertEquals(trackPurchaseRequest.items.size(), 2);
        assertEquals(trackPurchaseRequest.total, new BigDecimal(101d));
    }

    @Test
    public void testGetPlaceholderEmailNoEnvironmentOrStamp() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(null);
        request.setDeviceApplicationStamp(null);
        Exception e = null;
        try {
            String email = IterableExtension.getPlaceholderEmail(request);
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
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("1234@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentButNoIds() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        request.setDeviceApplicationStamp("1234");
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("1234@placeholder.email", email);

        request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new IosRuntimeEnvironment());
        request.setDeviceApplicationStamp("12345");
        email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("12345@placeholder.email", email);

        request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        request.setDeviceApplicationStamp("123456");
        email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("123456@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentIDFA() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new IosRuntimeEnvironment());
        DeviceIdentity idfa = new DeviceIdentity(DeviceIdentity.Type.IOS_ADVERTISING_ID, Identity.Encoding.RAW, "foo-idfa");
        ((IosRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa));
        request.setDeviceApplicationStamp("1234");
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfa@placeholder.email", email);

        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        ((TVOSRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa));
        request.setDeviceApplicationStamp("1234");
        email = IterableExtension.getPlaceholderEmail(request);
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
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("foo-idfv@placeholder.email", email);

        request.setRuntimeEnvironment(new TVOSRuntimeEnvironment());
        ((TVOSRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfa, idfv));
        request.setDeviceApplicationStamp("1234");
        email = IterableExtension.getPlaceholderEmail(request);
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
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("foo-gaid@placeholder.email", email);
    }

    @Test
    public void testGetPlaceholderEmailEnvironmentAndroidID() throws Exception {
        EventProcessingRequest request = createEventProcessingRequest();
        request.setRuntimeEnvironment(new AndroidRuntimeEnvironment());
        DeviceIdentity idfv = new DeviceIdentity(DeviceIdentity.Type.ANDROID_ID, Identity.Encoding.RAW, "foo-aid");
        ((AndroidRuntimeEnvironment)request.getRuntimeEnvironment()).setIdentities(Arrays.asList(idfv));
        request.setDeviceApplicationStamp("1234");
        String email = IterableExtension.getPlaceholderEmail(request);
        assertEquals("foo-aid@placeholder.email", email);
    }

    @org.junit.Test
    public void testUpdateSubscriptionsEvent() throws Exception {
        IterableExtension extension = new IterableExtension();
        extension.iterableService = Mockito.mock(IterableService.class);
        Call callMock = Mockito.mock(Call.class);
        Mockito.when(extension.iterableService.updateSubscriptions(Mockito.any(), Mockito.any()))
                .thenReturn(callMock);
        Mockito.when(extension.iterableService.track(Mockito.any(), Mockito.any()))
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

        extension.processCustomEvent(event);

        ArgumentCaptor<UpdateSubscriptionsRequest> argument = ArgumentCaptor.forClass(UpdateSubscriptionsRequest.class);
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(extension.iterableService).updateSubscriptions(stringArgumentCaptor.capture(), argument.capture());
        assertEquals("foo api key 2", stringArgumentCaptor.getValue());
        assertEquals("mptest@mparticle.com", argument.getValue().email);
        assertEquals(expectedEmailListIdList, argument.getValue().emailListIds);
        assertEquals(expectedChannelIdList, argument.getValue().unsubscribedChannelIds);
        assertEquals(expectedMessageTypeIdList, argument.getValue().unsubscribedMessageTypeIds);
        assertEquals(expectedCampaignId, (int)argument.getValue().campaignId);
        assertEquals(expectedTemplateId, (int)argument.getValue().templateId);

        apiResponse.code = "anything but success";

        IOException exception = null;
        try {
            extension.processCustomEvent(event);
        } catch (IOException ioe) {
            exception = ioe;
        }
        assertNotNull("Iterable extension should have thrown an IOException", exception);
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

package com.mparticle.ext.iterable;

import com.mparticle.sdk.MessageProcessor;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeResponse;
import com.mparticle.sdk.model.audienceprocessing.AudienceSubscriptionRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceSubscriptionResponse;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.*;

import java.io.IOException;
import java.util.*;

public class IterableExtensionIngress extends MessageProcessor {

  public static final String NAME = "Iterable";
  public static final String SETTING_API_KEY = "apiKey";
  public static final String SETTING_GCM_NAME_KEY = "gcmIntegrationName";
  public static final String SETTING_APNS_KEY = "apnsProdIntegrationName";
  public static final String SETTING_APNS_SANDBOX_KEY = "apnsSandboxIntegrationName";
  public static final String SETTING_LIST_ID = "listId";
  public static final String SETTING_COERCE_STRINGS_TO_SCALARS = "coerceStringsToScalars";
  public static final String SETTING_USER_ID_FIELD = "userIdField";
  public static final String USER_ID_FIELD_CUSTOMER_ID = "customerId";

  @Override
  public ModuleRegistrationResponse processRegistrationRequest(ModuleRegistrationRequest request) {
    ModuleRegistrationResponse response = new ModuleRegistrationResponse(NAME, "1.6.0");

    Permissions permissions = new Permissions();
    permissions.setUserIdentities(
            Arrays.asList(
                    new UserIdentityPermission(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, false),
                    new UserIdentityPermission(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW, false)
            )
    );
    permissions.setDeviceIdentities(
            Arrays.asList(
                    new DeviceIdentityPermission(DeviceIdentity.Type.GOOGLE_CLOUD_MESSAGING_TOKEN, Identity.Encoding.RAW),
                    new DeviceIdentityPermission(DeviceIdentity.Type.APPLE_PUSH_NOTIFICATION_TOKEN, Identity.Encoding.RAW),
                    new DeviceIdentityPermission(DeviceIdentity.Type.IOS_VENDOR_ID, Identity.Encoding.RAW),
                    new DeviceIdentityPermission(DeviceIdentity.Type.ANDROID_ID, Identity.Encoding.RAW),
                    new DeviceIdentityPermission(DeviceIdentity.Type.GOOGLE_ADVERTISING_ID, Identity.Encoding.RAW)
            )
    );
    permissions.setAllowAccessDeviceApplicationStamp(true);
    permissions.setAllowUserAttributes(true);
    permissions.setAllowDeviceInformation(true);
    permissions.setAllowAccessMpid(true);
    response.setPermissions(permissions);
    response.setDescription("<a href=\"https://www.iterable.com\">Iterable</a> makes consumer growth marketing and user engagement simple. With Iterable, marketers send the right message, to the right device, at the right time.");
    EventProcessingRegistration eventProcessingRegistration = new EventProcessingRegistration()
            .setSupportedRuntimeEnvironments(
                    Arrays.asList(
                            RuntimeEnvironment.Type.ANDROID,
                            RuntimeEnvironment.Type.IOS,
                            RuntimeEnvironment.Type.MOBILEWEB,
                            RuntimeEnvironment.Type.UNKNOWN)
            );
    eventProcessingRegistration.setPushMessagingProviderId("itbl");

    List<Setting> eventSettings = new ArrayList<>();
    List<Setting> audienceSettings = new ArrayList<>();
    Setting apiKey = new TextSetting(SETTING_API_KEY, "API Key")
            .setIsRequired(true)
            .setIsConfidential(true)
            .setDescription("API key used to connect to the Iterable API - see the Integrations section of your Iterable account.");
    Setting userIdField = new TextSetting(SETTING_USER_ID_FIELD, "User ID")
            .setIsRequired(true)
            .setDefaultValue(USER_ID_FIELD_CUSTOMER_ID)
            .setDescription("Select which user identity to forward to Iterable as your customer's user ID.");

    audienceSettings.add(apiKey);
    audienceSettings.add(userIdField);

    eventSettings.add(apiKey);
    eventSettings.add(
            new TextSetting(SETTING_GCM_NAME_KEY, "GCM Push Integration Name")
                    .setIsRequired(false)
                    .setDescription("GCM integration name set up in the Mobile Push section of your Iterable account.")

    );
    eventSettings.add(
            new TextSetting(SETTING_APNS_SANDBOX_KEY, "APNS Sandbox Integration Name")
                    .setIsRequired(false)
                    .setDescription("APNS Sandbox integration name set up in the Mobile Push section of your Iterable account.")
    );
    eventSettings.add(
            new TextSetting(SETTING_APNS_KEY, "APNS Production Integration Name")
                    .setIsRequired(false)
                    .setDescription("APNS Production integration name set up in the Mobile Push section of your Iterable account.")
    );
    eventSettings.add(
            new BooleanSetting(SETTING_COERCE_STRINGS_TO_SCALARS, "Coerce Strings to Scalars")
                    .setIsChecked(true)
                    .setDescription("If enabled, mParticle will attempt to coerce string attributes into scalar types (integer, boolean, and float).")
    );
    eventProcessingRegistration.setAccountSettings(eventSettings);

    List<Setting> connectionSettings = new ArrayList<>();
    connectionSettings.add(userIdField);
    eventProcessingRegistration.setConnectionSettings(connectionSettings);

    // Specify supported event types
    List<Event.Type> supportedEventTypes = Arrays.asList(
            Event.Type.CUSTOM_EVENT,
            Event.Type.PUSH_SUBSCRIPTION,
            Event.Type.PUSH_MESSAGE_RECEIPT,
            Event.Type.PUSH_MESSAGE_OPEN,
            Event.Type.USER_IDENTITY_CHANGE,
            Event.Type.PRODUCT_ACTION);

    eventProcessingRegistration.setSupportedEventTypes(supportedEventTypes);
    response.setEventProcessingRegistration(eventProcessingRegistration);
    AudienceProcessingRegistration audienceRegistration = new AudienceProcessingRegistration();
    audienceRegistration.setAccountSettings(audienceSettings);
    List<Setting> subscriptionSettings = new LinkedList<>();
    IntegerSetting listIdSetting = new IntegerSetting(SETTING_LIST_ID, "List ID");
    listIdSetting.setIsRequired(true);
    listIdSetting.setDescription("The ID of the Iterable list to populate with the users from this segment.");
    subscriptionSettings.add(listIdSetting);
    audienceRegistration.setAudienceConnectionSettings(subscriptionSettings);
    response.setAudienceProcessingRegistration(audienceRegistration);

    return response;
  }

  /**
   * When a MessageProcessor is given a batch of data/events, it will first call this method. This
   * is a good time to do some setup.
   */
  @Override
  public EventProcessingResponse processEventProcessingRequest(EventProcessingRequest request)
      throws IOException {
    // do some setup, then call super. if you don't call super, you'll effectively short circuit
    // the whole thing, which isn't really fun for anyone.
    return super.processEventProcessingRequest(request);
  }

  @Override
  public void processPushMessageReceiptEvent(PushMessageReceiptEvent event) throws IOException {
    super.processPushMessageReceiptEvent(event);
  }

  @Override
  public void processPushSubscriptionEvent(PushSubscriptionEvent event) throws IOException {
    super.processPushSubscriptionEvent(event);
  }

  @Override
  public void processUserIdentityChangeEvent(UserIdentityChangeEvent event) throws IOException {
    super.processUserIdentityChangeEvent(event);
  }

  @Override
  public void processCustomEvent(CustomEvent event) throws IOException {
    super.processCustomEvent(event);
  }

  @Override
  public AudienceMembershipChangeResponse processAudienceMembershipChangeRequest(
      AudienceMembershipChangeRequest request) throws IOException {
    return super.processAudienceMembershipChangeRequest(request);
  }

  @Override
  /**
   * This method isn't supported by the Iterable Audience Integration. The
   * Integration requires mParticle end-users to supply the listId for a
   * pre-existing Iterable list.
   */
  public AudienceSubscriptionResponse processAudienceSubscriptionRequest(
      AudienceSubscriptionRequest request) throws UnsupportedOperationException {
      throw new UnsupportedOperationException("This feature isn't implemented");
  }
}

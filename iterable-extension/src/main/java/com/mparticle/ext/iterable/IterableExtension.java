package com.mparticle.ext.iterable;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.mparticle.iterable.*;
import com.mparticle.sdk.MessageProcessor;
import com.mparticle.sdk.model.audienceprocessing.Audience;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeResponse;
import com.mparticle.sdk.model.audienceprocessing.UserProfile;
import com.mparticle.sdk.model.eventprocessing.*;
import com.mparticle.sdk.model.registration.*;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class IterableExtension extends MessageProcessor {

    public static final String SETTING_API_KEY = "apiKey";
    public static final String SETTING_GCM_NAME_KEY = "gcmIntegrationName";
    public static final String SETTING_APNS_KEY = "apnsProdIntegrationName";
    public static final String SETTING_APNS_SANDBOX_KEY = "apnsSandboxIntegrationName";
    public static final String SETTING_LIST_ID = "listId";
    public static final String SETTING_COERCE_STRINGS_TO_SCALARS = "coerceStringsToScalars";
    public static final String SETTING_USER_ID_FIELD = "userIdField";
    public static final String USER_ID_FIELD_CUSTOMER_ID = "customerId";
    public static final String USER_ID_FIELD_MPID = "mpid";
    public static final String PLACEHOLDER_EMAIL_DOMAIN = "@placeholder.email";
    public static final String MPARTICLE_RESERVED_PHONE_ATTR = "$Mobile";
    public static final String ITERABLE_RESERVED_PHONE_ATTR = "phoneNumber";
    static Set<Integer> RETRIABLE_HTTP_STATUS_SET = new HashSet<>(Arrays.asList(429, 502, 504));
    IterableService iterableService = IterableService.newInstance();

    @Override
    public ModuleRegistrationResponse processRegistrationRequest(ModuleRegistrationRequest request) {
        // Processing ModuleRegistrationRequests is handled by the Ingress Lambda.
        return new ModuleRegistrationResponse("Iterable", "1.6.0");
    }

    @Override
    public EventProcessingResponse processEventProcessingRequest(EventProcessingRequest request) throws IOException {
        Collections.sort(
                request.getEvents(),
                (a, b) -> a.getTimestamp() > b.getTimestamp() ? 1 : a.getTimestamp() == b.getTimestamp() ? 0 : -1
        );
        insertPlaceholderEmail(request);
        updateUser(request);
        processPushOpens(request);
        return super.processEventProcessingRequest(request);
    }

    private void processPushOpens(EventProcessingRequest processingRequest) throws IOException {
        // Skip processing if the SDK is present - it tracks opens automatically
        if (hasBundledSDK(processingRequest)) {
            return;
        }

        if (processingRequest.getEvents() != null) {
            List<PushMessageOpenEvent> pushOpenEvents = processingRequest.getEvents().stream()
                    .filter(e -> e.getType() == Event.Type.PUSH_MESSAGE_OPEN)
                    .map(e -> (PushMessageOpenEvent) e)
                    .collect(Collectors.toList());

            for (PushMessageOpenEvent event : pushOpenEvents) {
                TrackPushOpenRequest request = new TrackPushOpenRequest();
                if (event.getPayload() != null && processingRequest.getUserIdentities() != null) {
                    addUserIdentitiesToRequest(request, processingRequest);
                    if (request.email == null && request.userId == null) {
                        IterableExtensionLogger.logError("Unable to process PushMessageOpenEvent - user has no email or customer id.");
                        return;
                    }
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> payload = mapper.readValue(event.getPayload(), Map.class);
                    if (payload.containsKey("itbl")) {
                        //Android and iOS have differently encoded payload formats. See the tests for examples.
                        Map<String, Object> iterableMap;
                        if (processingRequest.getRuntimeEnvironment() instanceof AndroidRuntimeEnvironment) {
                            iterableMap = mapper.readValue((String) payload.get("itbl"), Map.class);
                        } else {
                            iterableMap = (Map) payload.get("itbl");
                        }
                        request.campaignId = convertItblPayloadFieldToInt(iterableMap.get("campaignId"));
                        request.templateId = convertItblPayloadFieldToInt(iterableMap.get("templateId"));
                        request.messageId = (String) iterableMap.get("messageId");
                        if (request.campaignId == 0 || request.templateId == 0) {
                            // Proof sends don't have a campaignId
                            return;
                        }
                        request.createdAt = (int) (event.getTimestamp() / 1000.0);
                        Response<IterableApiResponse> response = iterableService.trackPushOpen(getApiKey(processingRequest), request).execute();
                        handleIterableResponse(response, event.getId());
                    }
                }
            }
        }
    }

    private static String getApiKey(Event event) {
        Account account = event.getRequest().getAccount();
        return account.getStringSetting(SETTING_API_KEY, true, null);
    }

    private static String getApiKey(EventProcessingRequest event) {
        Account account = event.getAccount();
        return account.getStringSetting(SETTING_API_KEY, true, null);
    }

    private static String getApiKey(AudienceMembershipChangeRequest event) {
        Account account = event.getAccount();
        return account.getStringSetting(SETTING_API_KEY, true, null);
    }

    /**
     * Verify that there's an email present, create a placeholder if not.
     *
     * @param request
     * @throws IOException
     */
    private void insertPlaceholderEmail(EventProcessingRequest request) throws IOException {
        long count = request.getUserIdentities() == null ? 0 : request.getUserIdentities().stream()
                .filter(t -> t.getType().equals(UserIdentity.Type.EMAIL))
                .count();
        if (count > 0) {
            return;
        }
        String placeholderEmail = getPlaceholderEmail(request);
        if (request.getUserIdentities() == null) {
            request.setUserIdentities(new ArrayList<>());
        }
        request.getUserIdentities().add(new UserIdentity(UserIdentity.Type.EMAIL, Identity.Encoding.RAW, placeholderEmail));
    }

    @Override
    public void processPushSubscriptionEvent(PushSubscriptionEvent event) throws IOException {
        // Skip processing if the SDK is present - it registers tokens automatically
        if (hasBundledSDK(event.getRequest())) {
            return;
        }

        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        if (PushSubscriptionEvent.Action.UNSUBSCRIBE.equals(event.getAction())) {
            return;
        }
        request.device = new Device();
        if (event.getRequest().getRuntimeEnvironment().getType().equals(RuntimeEnvironment.Type.IOS)) {
            Boolean sandboxed = ((IosRuntimeEnvironment) event.getRequest().getRuntimeEnvironment()).getIsSandboxed();
            if (sandboxed != null && sandboxed) {
                request.device.platform = Device.PLATFORM_APNS_SANDBOX;
                request.device.applicationName = event.getRequest().getAccount().getAccountSettings().get(SETTING_APNS_SANDBOX_KEY);
            } else {
                request.device.platform = Device.PLATFORM_APNS;
                request.device.applicationName = event.getRequest().getAccount().getAccountSettings().get(SETTING_APNS_KEY);
            }
        } else if (event.getRequest().getRuntimeEnvironment().getType().equals(RuntimeEnvironment.Type.ANDROID)) {
            request.device.platform = Device.PLATFORM_GCM;
            request.device.applicationName = event.getRequest().getAccount().getAccountSettings().get(SETTING_GCM_NAME_KEY);
        } else {
            IterableExtensionLogger.logError("Cannot process push subscription event for unknown RuntimeEnvironment type.");
            return;
        }

        request.device.token = event.getToken();

        try {
            UserIdentity email = event.getRequest().getUserIdentities().stream()
                    .filter(t -> t.getType().equals(UserIdentity.Type.EMAIL))
                    .findFirst()
                    .get();
            request.email = email.getValue();
        } catch (NoSuchElementException e) {
            IterableExtensionLogger.logError("Unable to construct Iterable RegisterDeviceTokenRequest - no user email.");
            return;
        }

        Response<IterableApiResponse> response = iterableService.registerToken(getApiKey(event), request).execute();
        handleIterableResponse(response, event.getId());
    }

    void updateUser(EventProcessingRequest request) throws IOException {
        if (request.getEvents() != null) {

            List<UserIdentityChangeEvent> emailChangeEvents = request.getEvents().stream()
                    .filter(e -> e.getType() == Event.Type.USER_IDENTITY_CHANGE)
                    .map(e -> (UserIdentityChangeEvent) e)
                    .filter(e -> e.getAdded() != null && e.getRemoved() != null)
                    .filter(e -> e.getAdded().size() > 0 && e.getRemoved().size() > 0)
                    .filter(e -> e.getAdded().get(0).getType().equals(UserIdentity.Type.EMAIL))
                    .filter(e -> !isEmpty(e.getAdded().get(0).getValue()) && !isEmpty(e.getRemoved().get(0).getValue()))
                    .collect(Collectors.toList());

            List<UserIdentityChangeEvent> emailAddedEvents = request.getEvents().stream()
                    .filter(e -> e.getType() == Event.Type.USER_IDENTITY_CHANGE)
                    .map(e -> (UserIdentityChangeEvent) e)
                    .filter(e -> e.getAdded() != null && e.getAdded().size() > 0)
                    .filter(e -> e.getRemoved() == null || e.getRemoved().size() == 0)
                    .filter(e -> e.getAdded().get(0).getType().equals(UserIdentity.Type.EMAIL))
                    .filter(e -> !isEmpty(e.getAdded().get(0).getValue()))
                    .collect(Collectors.toList());

            String placeholderEmail = getPlaceholderEmail(request);
            //convert from placeholder to email now that we have one
            for (UserIdentityChangeEvent changeEvent : emailAddedEvents) {
                UpdateEmailRequest updateEmailRequest = new UpdateEmailRequest();
                updateEmailRequest.currentEmail = placeholderEmail;
                //this is safe due to the filters above
                updateEmailRequest.newEmail = changeEvent.getAdded().get(0).getValue();
                Response<IterableApiResponse> response = iterableService.updateEmail(getApiKey(request), updateEmailRequest).execute();
                handleIterableResponse(response, changeEvent.getId());
            }

            //convert from old to new email
            for (UserIdentityChangeEvent changeEvent : emailChangeEvents) {
                UpdateEmailRequest updateEmailRequest = new UpdateEmailRequest();
                //these are safe due to the filters above
                updateEmailRequest.currentEmail = changeEvent.getRemoved().get(0).getValue();
                updateEmailRequest.newEmail = changeEvent.getAdded().get(0).getValue();
                Response<IterableApiResponse> response = iterableService.updateEmail(getApiKey(request), updateEmailRequest).execute();
                handleIterableResponse(response, changeEvent.getId());
            }
        }

        UserUpdateRequest userUpdateRequest = new UserUpdateRequest();
        if (request.getUserIdentities() != null) {
            addUserIdentitiesToRequest(userUpdateRequest, request);
            if (!isEmpty(userUpdateRequest.email) || !isEmpty(userUpdateRequest.userId)) {
                userUpdateRequest.dataFields = convertAttributes(request.getUserAttributes(), shouldCoerceStrings(request));
                Response<IterableApiResponse> response = iterableService.userUpdate(getApiKey(request), userUpdateRequest).execute();
                handleIterableResponse(response, request.getId());
            }
        }
    }

    private Map<String, Object> convertAttributes(Map<String, String> attributes, boolean coerceStringsToScalars) {
        if (attributes == null) {
            return null;
        }
        Map<String, String> attributesWithReserved = convertReservedAttributes(attributes);
        
        if (coerceStringsToScalars) {
            return attemptTypeConversion(attributesWithReserved);
        } else {
            Map<String, Object> mapObj = new HashMap<String, Object>();
            mapObj.putAll(attributesWithReserved);

            return mapObj;
        }
    }

    private static Map<String, String> convertReservedAttributes(Map<String, String> attributes) {
        Map<String, String> convertedAttrs = new HashMap<String, String>();
        convertedAttrs.putAll(attributes);
        if (convertedAttrs.containsKey(MPARTICLE_RESERVED_PHONE_ATTR)) {
            String phoneNumber = convertedAttrs.get(MPARTICLE_RESERVED_PHONE_ATTR);
            String formattedNumber = phoneNumber.replaceAll("[^0-9+]", "");
            convertedAttrs.put(ITERABLE_RESERVED_PHONE_ATTR, formattedNumber);
            convertedAttrs.remove(MPARTICLE_RESERVED_PHONE_ATTR);
        }
        return convertedAttrs;
    }

    private static boolean shouldCoerceStrings(EventProcessingRequest request) {
        String settingValue = request.getAccount().getAccountSettings().get(SETTING_COERCE_STRINGS_TO_SCALARS);
        
        if (settingValue == null) { 
            return false;
        }
        return Boolean.parseBoolean(settingValue);
    }

    private static boolean shouldUseMPID(Account account) {
        String settingValue = account.getStringSetting(SETTING_USER_ID_FIELD,false, USER_ID_FIELD_CUSTOMER_ID);
        return settingValue.equals(USER_ID_FIELD_MPID);
    }

    private static boolean isEmpty(CharSequence chars) {
        return chars == null || "".equals(chars);
    }

    /**
     * Map an mParticle `purchase` product_action to Iterable's `trackPurchase` request.
     *
     * mParticle product_action: https://docs.mparticle.com/developers/server/json-reference#product_action
     * Iterable trackPurchase: https://api.iterable.com/api/docs#commerce_trackPurchase
     *
     * @param event the mParticle event
     * @throws IOException
     */
    @Override
    public void processProductActionEvent(ProductActionEvent event) throws IOException {
        if (event.getAction().equals(ProductActionEvent.Action.PURCHASE)) {
            TrackPurchaseRequest purchaseRequest = new TrackPurchaseRequest();
            if (event.getId() != null) {
                purchaseRequest.id = event.getId().toString();
            }
            purchaseRequest.createdAt = (int) (event.getTimestamp() / 1000.0);
            ApiUser apiUser = new ApiUser();
            addUserIdentitiesToRequest(apiUser, event.getRequest());
            boolean shouldCoerceStrings = shouldCoerceStrings(event.getRequest());
            apiUser.dataFields = convertAttributes(event.getRequest().getUserAttributes(), shouldCoerceStrings);
            purchaseRequest.user = apiUser;
            purchaseRequest.total = event.getTotalAmount();
            if (event.getProducts() != null) {
                purchaseRequest.items = event.getProducts().stream()
                        .map(p -> convertToCommerceItem(p, shouldCoerceStrings))
                        .collect(Collectors.toList());
            }

            Response<IterableApiResponse> response = iterableService.trackPurchase(getApiKey(event), purchaseRequest).execute();
            handleIterableResponse(response, event.getId());
        }
    }

    CommerceItem convertToCommerceItem(Product product, boolean shouldCoerceStrings) {
        CommerceItem item = new CommerceItem();
        item.dataFields = convertAttributes(product.getAttributes(), shouldCoerceStrings);
        //iterable requires ID, and they also take SKU. mParticle doesn't differentiate
        //between sku and id. so, use our sku/id for both in Iterable:
        item.id = item.sku = product.getId();
        item.name = product.getName();
        item.price = product.getPrice();
        if (product.getQuantity() != null) {
            item.quantity = product.getQuantity().intValue();
        }
        if (product.getCategory() != null) {
            item.categories = new LinkedList<>();
            item.categories.add(product.getCategory());
        }
        return item;
    }

    @Override
    public void processUserAttributeChangeEvent(UserAttributeChangeEvent event) {
        //there's no reason to do this - it's already done at the start of batch processing
        //updateUser(event.getContext());
    }

    /**
     *
     * Make the best attempt at creating a placeholder email, prioritize:
     *  1. MPID if enabled
     *  2. Platform respective device IDs
     *  3. customer Id
     *  4. device application stamps
     * @param request
     * @return
     *
     * Also see: https://support.iterable.com/hc/en-us/articles/208499956-Creating-user-profiles-without-an-email-address
     */
    static String getPlaceholderEmail(EventProcessingRequest request) throws IOException {
        String id = null;
        if (shouldUseMPID(request.getAccount())) {
            id = request.getMpId();
        } else {
            if (request.getRuntimeEnvironment() instanceof IosRuntimeEnvironment || request.getRuntimeEnvironment() instanceof TVOSRuntimeEnvironment ) {
                List<DeviceIdentity> deviceIdentities = null;
                if (request.getRuntimeEnvironment() instanceof IosRuntimeEnvironment) {
                    deviceIdentities = ((IosRuntimeEnvironment) request.getRuntimeEnvironment()).getIdentities();
                } else {
                    deviceIdentities = ((TVOSRuntimeEnvironment) request.getRuntimeEnvironment()).getIdentities();
                }
                if (deviceIdentities != null) {
                    DeviceIdentity deviceIdentity = deviceIdentities.stream().filter(t -> t.getType().equals(DeviceIdentity.Type.IOS_VENDOR_ID))
                            .findFirst()
                            .orElse(null);
                    if (deviceIdentity != null) {
                        id = deviceIdentity.getValue();
                    }
                    if (isEmpty(id)) {
                        deviceIdentity = deviceIdentities.stream().filter(t -> t.getType().equals(DeviceIdentity.Type.IOS_ADVERTISING_ID))
                                .findFirst()
                                .orElse(null);
                        if (deviceIdentity != null) {
                            id = deviceIdentity.getValue();
                        }
                    }
                }
            } else if (request.getRuntimeEnvironment() instanceof AndroidRuntimeEnvironment) {
                if (((AndroidRuntimeEnvironment)request.getRuntimeEnvironment()).getIdentities() != null) {
                    DeviceIdentity deviceIdentity = ((AndroidRuntimeEnvironment)request.getRuntimeEnvironment()).getIdentities().stream().filter(t -> t.getType().equals(DeviceIdentity.Type.GOOGLE_ADVERTISING_ID))
                            .findFirst()
                            .orElse(null);
                    if (deviceIdentity != null) {
                        id = deviceIdentity.getValue();
                    }
                    if (isEmpty(id)) {
                        deviceIdentity = ((AndroidRuntimeEnvironment) request.getRuntimeEnvironment()).getIdentities().stream().filter(t -> t.getType().equals(DeviceIdentity.Type.ANDROID_ID))
                                .findFirst()
                                .orElse(null);
                        if (deviceIdentity != null) {
                            id = deviceIdentity.getValue();
                        }
                    }
                }
            }

            if (isEmpty(id)) {
                if (request.getUserIdentities() != null) {
                    UserIdentity customerId = request.getUserIdentities().stream()
                            .filter(t -> t.getType().equals(UserIdentity.Type.CUSTOMER))
                            .findFirst()
                            .orElse(null);
                    if (customerId != null) {
                        id = customerId.getValue();
                    }
                }
            }

            if (isEmpty(id)) {
                id = request.getDeviceApplicationStamp();
            }
        }

        if (isEmpty(id)) {
            // This error should stop processing for the entire batch.
            throw new IOException("Unable to send user to Iterable - no email and unable to construct placeholder.");
        }
        return id + PLACEHOLDER_EMAIL_DOMAIN;
    }

    private static List<Integer> convertToIntList(String csv){
        if (csv == null) {
            return null;
        } else if (csv.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> list = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(csv, ",");
        while (st.hasMoreTokens()) {
            list.add(Integer.parseInt(st.nextToken().trim()));
        }
        return list;
    }

    public static final String UPDATE_SUBSCRIPTIONS_CUSTOM_EVENT_NAME = "subscriptionsUpdated";
    public static final String EMAIL_LIST_ID_LIST_KEY = "emailListIds";
    public static final String UNSUBSCRIBE_CHANNEL_ID_LIST_KEY = "unsubscribedChannelIds";
    public static final String UNSUBSCRIBE_MESSAGE_TYPE_ID_LIST_KEY = "unsubscribedMessageTypeIds";
    public static final String CAMPAIGN_ID_KEY = "campaignId";
    public static final String TEMPLATE_ID_KEY = "templateId";

    /**
     *
     * This is expected to be called with an event that conforms to the following:
     * Name: "updateSubscriptions"
     *
     * And has at least some of the following:
     *
     * Attribute: emailListIds
     * Attribute: unsubscribedChannelIds
     * Attribute: unsubscribedMessageTypeIds
     * Attribute: campaignId
     * Attribute: templateId
     *
     */
    private boolean processSubscribeEvent(CustomEvent event) throws IOException {
        UpdateSubscriptionsRequest updateRequest = generateSubscriptionRequest(event);
        if (updateRequest == null) {
            return false;
        }
        Response<IterableApiResponse> response = iterableService.updateSubscriptions(getApiKey(event), updateRequest).execute();
        handleIterableResponse(response, event.getId());
        return true;

    }

    static UpdateSubscriptionsRequest generateSubscriptionRequest(CustomEvent event) {
        if (!UPDATE_SUBSCRIPTIONS_CUSTOM_EVENT_NAME.equalsIgnoreCase(event.getName())) {
            return null;
        }
        UpdateSubscriptionsRequest updateRequest = new UpdateSubscriptionsRequest();

        Map<String, String> eventAttributes = event.getAttributes();
        updateRequest.emailListIds = convertToIntList(eventAttributes.get(EMAIL_LIST_ID_LIST_KEY));
        updateRequest.unsubscribedChannelIds = convertToIntList(eventAttributes.get(UNSUBSCRIBE_CHANNEL_ID_LIST_KEY));
        updateRequest.unsubscribedMessageTypeIds = convertToIntList(eventAttributes.get(UNSUBSCRIBE_MESSAGE_TYPE_ID_LIST_KEY));

        String campaignId = eventAttributes.get(CAMPAIGN_ID_KEY);
        if (!isEmpty(campaignId)) {
            try {
                updateRequest.campaignId = Integer.parseInt(campaignId.trim());
            }catch (NumberFormatException ignored) {

            }
        }

        String templateId = eventAttributes.get(TEMPLATE_ID_KEY);
        if (!isEmpty(templateId)) {
            try {
                updateRequest.templateId = Integer.parseInt(templateId.trim());
            }catch (NumberFormatException ignored) {

            }
        }

        List<UserIdentity> identities = event.getRequest().getUserIdentities();
        if (identities != null) {
            for (UserIdentity identity : identities) {
                if (identity.getType().equals(UserIdentity.Type.EMAIL)) {
                    updateRequest.email = identity.getValue();
                }
            }
        }
        return updateRequest;
    }

    /**
     * Map an mParticle `custom_event` to Iterable's `track` request.
     *
     * mParticle custom_event: https://docs.mparticle.com/developers/server/json-reference/#custom_event
     * Iterable track: https://api.iterable.com/api/docs#events_track
     *
     * @param event the mParticle event
     */
    @Override
    public void processCustomEvent(CustomEvent event) throws IOException {
        if (processSubscribeEvent(event)) {
            return;
        }

        TrackRequest request = new TrackRequest(event.getName());
        if (event.getId() != null) {
            request.id = event.getId().toString();
        }
        request.createdAt = (int) (event.getTimestamp() / 1000.0);
        request.dataFields = attemptTypeConversion(event.getAttributes());
        addUserIdentitiesToRequest(request, event.getRequest());

        Response<IterableApiResponse> response = iterableService.track(getApiKey(event), request).execute();
        handleIterableResponse(response, event.getId());
    }

    /**
     * Make a best-effort attempt to coerce the values of each map item to bool, double, int, and string types
     *
     * mParticle's API only accepts string, whereas Iterable's API accept different types. By coercing these types,
     * users of the Iterable API are able to create campaigns, aggregate events, etc.
     *
     * @param attributes
     * @return
     */
    private Map<String, Object> attemptTypeConversion(Map<String, String> attributes) {
        if (attributes == null) {
            return null;
        }
        Map<String, Object> converted = new HashMap<>(attributes.size());
        attributes.forEach((key,value)-> {
            if (isEmpty(value)) {
                converted.put(key, value);
            } else {
                if (value.toLowerCase(Locale.US).equals("true") || value.toLowerCase(Locale.US).equals("false")) {
                    converted.put(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        double doubleValue = Double.parseDouble(value);
                        if ((doubleValue % 1) == 0) {
                            converted.put(key, Integer.parseInt(value));
                        } else {
                            converted.put(key, doubleValue);
                        }
                    }catch (NumberFormatException nfe) {
                        converted.put(key, value);
                    }
                }
            }

        });
        return converted;
    }

    /**
     * Map mParticle `PushMessageReceiptEvent` to Iterable `trackPushOpen` request.
     *
     * mParticle push_message event https://docs.mparticle.com/developers/server/json-reference#push_message
     * Iterable trackPushOpen request: https://api.iterable.com/api/docs#events_trackPushOpen
     *
     * @param event the mParticle event
     */
    @Override
    public void processPushMessageReceiptEvent(PushMessageReceiptEvent event) throws IOException {
        // Skip processing if the SDK is present - it tracks opens automatically
        if (hasBundledSDK(event.getRequest())) {
            return;
        }

        TrackPushOpenRequest request = new TrackPushOpenRequest();
        if (event.getPayload() != null && event.getRequest().getUserIdentities() != null) {
            addUserIdentitiesToRequest(request, event.getRequest());
            if (request.email == null && request.userId == null) {
                IterableExtensionLogger.logError("Unable to process PushMessageReceiptEvent - user has no email or customer id.");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(event.getPayload(), Map.class);
            if (payload.containsKey("itbl")) {
                //Android and iOS have differently encoded payload formats. See the tests for examples.
                Map<String, Object> iterableMap;
                if (event.getRequest().getRuntimeEnvironment() instanceof AndroidRuntimeEnvironment) {
                    iterableMap = mapper.readValue((String) payload.get("itbl"), Map.class);
                } else {
                    iterableMap = (Map) payload.get("itbl");
                }
                request.campaignId = convertItblPayloadFieldToInt(iterableMap.get("campaignId"));
                request.templateId = convertItblPayloadFieldToInt(iterableMap.get("templateId"));
                request.messageId = (String) iterableMap.get("messageId");
                if (request.campaignId == 0 || request.templateId == 0) {
                    // Proof sends don't have a campaignId
                    return;
                }
                request.createdAt = (int) (event.getTimestamp() / 1000.0);
                Response<IterableApiResponse> response = iterableService.trackPushOpen(getApiKey(event), request).execute();
                handleIterableResponse(response, event.getId());
            }
        }
    }

    public static Integer convertItblPayloadFieldToInt(Object itblField) {
        if (itblField instanceof Integer) {
            return (Integer) itblField;
        } else {
            return 0;
        }
    }

    /**
     * Map an mParticle `AudienceMembershipChangeRequest` to Iterable list `subscribe` and `unsubscribe` requests.
     *
     * Each subscribe and unsubscribe request may contain multiple users if there are multiple
     * users being added or removed from the same list. No dataFields are sent with the users.
     *
     * mParticle Audience Processing: https://docs.mparticle.com/developers/partners/firehose/#audience-processing
     * Iterable subscribe: https://api.iterable.com/api/docs#lists_subscribe
     * Iterable unsubscribe: https://api.iterable.com/api/docs#lists_unsubscribe
     *
     * @param request the mParticle request
     * @return a response that indicates the request was processed successfully
     */
    @Override
    public AudienceMembershipChangeResponse processAudienceMembershipChangeRequest(AudienceMembershipChangeRequest request) throws IOException {
        HashMap<Integer, List<ApiUser>> additions = new HashMap<>();
        HashMap<Integer, List<ApiUser>> removals = new HashMap<>();
        for (UserProfile profile : request.getUserProfiles()) {
            UserRequest userRequest = new UserRequest() {};
            addUserIdentitiesToRequest(userRequest, profile, request.getAccount());
            if (userRequest.email != null) {
                if (profile.getAudiences() != null) {
                    List<Audience> addedAudiences = profile.getAudiences().stream()
                            .filter(audience -> audience.getAudienceAction() == Audience.AudienceAction.ADD)
                            .collect(Collectors.toList());
                    List<Audience> removedAudiences = profile.getAudiences().stream()
                            .filter(audience -> audience.getAudienceAction() == Audience.AudienceAction.DELETE)
                            .collect(Collectors.toList());
                    for (Audience audience : addedAudiences) {
                        Map<String, String> audienceSettings = audience.getAudienceSubscriptionSettings();
                        int listId = Integer.parseInt(audienceSettings.get(SETTING_LIST_ID));
                        ApiUser user = new ApiUser();
                        user.email = userRequest.email;
                        user.userId = userRequest.userId;
                        if (!additions.containsKey(listId)) {
                            additions.put(listId, new LinkedList<>());
                        }
                        additions.get(listId).add(user);
                    }
                    for (Audience audience : removedAudiences) {
                        Map<String, String> audienceSettings = audience.getAudienceSubscriptionSettings();
                        int listId = Integer.parseInt(audienceSettings.get(SETTING_LIST_ID));
                        ApiUser user = new ApiUser();
                        user.email = userRequest.email;
                        user.userId = userRequest.userId;
                        if (!removals.containsKey(listId)) {
                            removals.put(listId, new LinkedList<>());
                        }
                        removals.get(listId).add(user);
                    }
                }
            }
        }

        for (Map.Entry<Integer, List<ApiUser>> entry : additions.entrySet()) {
            SubscribeRequest subscribeRequest = new SubscribeRequest();
            subscribeRequest.listId = entry.getKey();
            subscribeRequest.subscribers = entry.getValue();
            try {
                Response<ListResponse> response = iterableService.listSubscribe(getApiKey(request), subscribeRequest).execute();
                handleIterableListResponse(response, request.getId());
            } catch (Exception e) {
                Boolean isApiKeyNull = getApiKey(request) == null;
                IterableExtensionLogger.logError("A " + e.getClass() + "exception occurred." +
                        "ApiKey null: " + isApiKeyNull);
                e.printStackTrace();
            }

        }

        for (Map.Entry<Integer, List<ApiUser>> entry : removals.entrySet()) {
            UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest();
            unsubscribeRequest.listId = entry.getKey();
            unsubscribeRequest.subscribers = entry.getValue();
            try {
                Response<ListResponse> response = iterableService.listUnsubscribe(getApiKey(request), unsubscribeRequest).execute();
                handleIterableListResponse(response, request.getId());
            } catch (Exception e) {
                Boolean isApiKeyNull = getApiKey(request) == null;
                IterableExtensionLogger.logError("A " + e.getClass() + "exception occurred." +
                        "ApiKey null: " + isApiKeyNull);
                e.printStackTrace();
        }
    }
        return new AudienceMembershipChangeResponse();
    }

    private void addUserIdentitiesToRequest(UserRequest request, EventProcessingRequest processingRequest) {
        addUserIdentitiesToRequest(request, processingRequest.getUserIdentities(), processingRequest.getAccount(), processingRequest.getMpId());
    }

    private void addUserIdentitiesToRequest(UserRequest request, UserProfile userProfile, Account account) {
        addUserIdentitiesToRequest(request, userProfile.getUserIdentities(), account, userProfile.getMpId());
    }

    private void addUserIdentitiesToRequest(UserRequest request, List<UserIdentity> identities, Account account, String mpid) {
        if (identities != null) {
            for (UserIdentity identity : identities) {
                if (identity.getType().equals(UserIdentity.Type.EMAIL)) {
                    request.email = identity.getValue();
                } else if (identity.getType().equals(UserIdentity.Type.CUSTOMER) && !shouldUseMPID(account)) {
                    request.userId = identity.getValue();
                }
            }
        }
        if (shouldUseMPID(account)) {
            request.userId = mpid;
            if (request.email == null) {
                request.email = mpid + PLACEHOLDER_EMAIL_DOMAIN;
            }
        }
    }

    private boolean hasBundledSDK(EventProcessingRequest processingRequest) {
        Map<String, String> integrationAttributes = processingRequest.getIntegrationAttributes();
        return integrationAttributes != null &&
                integrationAttributes.getOrDefault("Iterable.sdkVersion", null) != null;
    }

    static void handleIterableResponse(Response<IterableApiResponse> response, UUID eventId) throws RetriableError {
        Boolean isResponseBodySuccess = response.body() != null && response.body().isSuccess();
        if (!response.isSuccessful() || !isResponseBodySuccess) {
            IterableExtensionLogger.logApiError(response, eventId);
            if (RETRIABLE_HTTP_STATUS_SET.contains(response.code())) {
                throw new RetriableError();
            }
        }
    }

    static void handleIterableListResponse(Response<ListResponse> response, UUID audienceRequestId) throws RetriableError {
        if (!response.isSuccessful()) {
            IterableExtensionLogger.logApiError(response, audienceRequestId);
            if (RETRIABLE_HTTP_STATUS_SET.contains(response.code())) {
                throw new RetriableError();
            }
        }
        Boolean hasFailures = response.body().failCount > 0;
        if (hasFailures) {
            IterableExtensionLogger.logError(
                    "List subscribe or unsubscribe request failed count: " + response.body().failCount);
        }
    }
}

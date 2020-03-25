package com.mparticle.iterable;

import java.util.Map;

public class TrackRequest extends UserRequest {

    /**
     * Required
     *
     * Name of event,
     */
    private String eventName;
    /**
     *  Time event happened. Set to the time event was received if unspecified. Expects a unix timestamp.
     */
    public Integer createdAt;
    /**
     *  Additional data associated with event (i.e. item id, item amount),
     */
    public Map<String, Object> dataFields;
    /**
     * Campaign tied to conversion
     */
    public Integer campaignId;

    public Integer templateId;

    public TrackRequest() {
        super();
    }

    public TrackRequest(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return this.eventName;
    }
}

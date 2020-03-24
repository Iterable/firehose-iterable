package com.mparticle.iterable;

import java.util.Map;

public abstract class IterableRequest extends UserRequest {

    /**
     *  Time event happened. Set to the time event was received if unspecified. Expects a unix timestamp.,
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

    public IterableRequest() {
        super();
    }


}

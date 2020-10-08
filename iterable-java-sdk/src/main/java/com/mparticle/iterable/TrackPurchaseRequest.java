package com.mparticle.iterable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TrackPurchaseRequest {
    /**
     * Optional purchase id. If a purchase event already exists with this id, the event will be
     * updated.
     */
    public String id;

    public ApiUser user;
    public List<CommerceItem> items;
    public Integer campaignId;
    public Integer templateId;
    public BigDecimal total;
    public Integer createdAt;
    public Map<String, Object> dataFields;
}

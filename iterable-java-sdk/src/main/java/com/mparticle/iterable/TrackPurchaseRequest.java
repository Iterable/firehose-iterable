package com.mparticle.iterable;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TrackPurchaseRequest {
    /**
     * Optional purchase id. If a purchase already exists with this id, the event will be updated. If none
     * is specified, a new id will automatically be generated and returned.
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

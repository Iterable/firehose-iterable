package com.mparticle.iterable;

import java.util.List;

public class UpdateSubscriptionsRequest extends UserRequest {
    public List<Integer> emailListIds;
    public List<Integer> unsubscribedChannelIds;
    public List<Integer> unsubscribedMessageTypeIds;
    public Integer campaignId;
    public Integer templateId;
}

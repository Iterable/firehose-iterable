package com.mparticle.iterable;

import java.util.List;

public class UnsubscribeRequest {
    public Integer listId;
    public List<ApiUser> subscribers;
    public Integer campaignId;
    public Boolean channelUnsubscribe;
}

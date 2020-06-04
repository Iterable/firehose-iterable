package com.mparticle.ext.iterable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mparticle.sdk.model.Message;

public class SuccessResponse {
    @JsonProperty(value="statusCode", required=true)
    public int statusCode;

    @JsonProperty(value="body", required=true)
    public Message body;

    public SuccessResponse(int statusCode, Message body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Message getBody() {
        return body;
    }

    public void setBody(Message body) {
        this.body = body;
    }
}

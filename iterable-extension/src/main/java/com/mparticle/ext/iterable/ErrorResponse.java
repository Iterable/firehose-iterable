package com.mparticle.ext.iterable;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ErrorResponse {
    @JsonProperty(value="statusCode", required=true)
    public int statusCode;

    @JsonProperty(value="body", required=true)
    public Map<String, String> body;

    public ErrorResponse() {}

    public ErrorResponse(int statusCode, Map<String, String> body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public void setBody(Map<String, String> body) {
        this.body = body;
    }
}

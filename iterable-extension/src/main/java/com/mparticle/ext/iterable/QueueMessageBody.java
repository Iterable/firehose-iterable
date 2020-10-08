package com.mparticle.ext.iterable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueMessageBody {
    @JsonProperty(value = "body", required = true)
    public String body;
}

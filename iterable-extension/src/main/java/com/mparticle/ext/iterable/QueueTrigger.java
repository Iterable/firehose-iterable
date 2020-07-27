package com.mparticle.ext.iterable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueTrigger {
  @JsonProperty(value = "Records", required = true)
  public List<QueueMessageBody> records;
}

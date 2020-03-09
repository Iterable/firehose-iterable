package com.mparticle.iterable;


import java.util.Map;

public class Device {
    public String token;
    public String platform;
    public String applicationName;
    public Map<String, Object> dataFields;
    public static String PLATFORM_APNS = "APNS";
    public static String PLATFORM_APNS_SANDBOX = "APNS_SANDBOX";
    public static String PLATFORM_GCM = "GCM";
}

package com.adobe.aep.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;

public class CloudWatchMetric {

    public static class Detail {
        public ArrayList<Metric> metrics;
    }

    public static class Dimension {
        @JsonProperty("Name")
        public String name;
        @JsonProperty("Value")
        public String value;
    }

    public static class Metric {
        @JsonProperty("Namespace")
        public String namespace;
        @JsonProperty("MetricName")
        public String metricName;
        @JsonProperty("Dimensions")
        public ArrayList<Dimension> dimensions;
        @JsonProperty("Value")
        public double value;
        @JsonProperty("Unit")
        public String unit;
        @JsonProperty("Timestamp")
        public int timestamp;
    }

    public String version;
    public String id;
    @JsonProperty("detail-type")
    public String detailType;
    public String source;
    public String account;
    public Date time;
    public String region;
    public ArrayList<Object> resources;
    public Detail detail;
}


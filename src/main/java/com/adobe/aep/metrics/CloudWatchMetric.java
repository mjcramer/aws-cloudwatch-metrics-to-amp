package com.adobe.aep.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class CloudWatchMetric {

    public static class Value {
        public double min;
        public double max;
        public double sum;
        public double count;
    }

    @JsonProperty("metric_stream_name")
    public String metricsStreamName;
    @JsonProperty("account_id")
    public String accountId;
    public String region;
    public String namespace;
    @JsonProperty("metric_name")
    public String metricName;
    public Map<String, String> dimensions;
    public long timestamp; // milliseconds!
    public Value value;
    @JsonProperty("Unit")
    public String unit;
}

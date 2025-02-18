package com.adobe.aep.metrics.records;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CloudWatchStreamMetric(
        @JsonProperty("metric_stream_name")
        String metricsStreamName,
        @JsonProperty("account_id")
        String accountId,
        String region,
        String namespace,
        @JsonProperty("metric_name")
        String metricName,
        Map<String, String> dimensions,
        long timestamp, // milliseconds!
        Value value,
        String unit
) {
}

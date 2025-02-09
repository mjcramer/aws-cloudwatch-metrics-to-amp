package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricStreamData {
    private String metricStreamName;
    private String accountID;
    private String region;
    private String namespace;
    private String metricName;
    private Value value;
    private Map<String, String> dimensions;
    private long timestamp;
    private String unit;

    // Getters and setters remain the same
    public String getMetricStreamName() {
        return metricStreamName;
    }

    public void setMetricStreamName(String metricStreamName) {
        this.metricStreamName = metricStreamName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public Map<String, String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, String> dimensions) {
        this.dimensions = dimensions;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }


    public static class Value {
        private double count;
        private double sum;
        private double max;
        private double min;

        public double getCount() {
            return count;
        }

        public void setCount(double count) {
            this.count = count;
        }
    }
}
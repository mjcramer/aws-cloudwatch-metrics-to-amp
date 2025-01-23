package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.Gauge;
import io.prometheus.client.Collector;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.util.stream.Stream;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

public class LambdaHandler implements RequestHandler<KinesisFirehoseEvent, LambdaHandler.KinesisFirehoseResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public KinesisFirehoseResponse handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {
        KinesisFirehoseResponse response = new KinesisFirehoseResponse();
        List<KinesisFirehoseResponse.Record> responseRecords = new ArrayList<>();

        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            String data = new String(record.getData().array());
            context.getLogger().log("Decoded data: " + data); // Log the decoded data

            // Fix: Ensure no extra or empty iteration occurs
            String[] splitRecord = data.split("\n");
            for (String x : splitRecord) {
                if (x.trim().isEmpty()) {
                    context.getLogger().log("Empty or invalid record skipped.");
                    continue;
                }

                try {
                    context.getLogger().log("Processing record: " + x);
                    MetricStreamData metricStreamData = objectMapper.readValue(x, MetricStreamData.class);
                    context.getLogger().log("Parsed MetricStreamData: " + metricStreamData); // Log the parsed object

                    // Log individual fields of MetricStreamData
                    String metricName = metricStreamData.getMetricName();
                    context.getLogger().log("Metric Name: " + (metricName != null ? metricName : "null"));

                    // Ensure the value field is not null and count is accessible
                    Double valueCount = (metricStreamData.getValue() != null)
                            ? metricStreamData.getValue().count
                            : null;

                    context.getLogger().log("Value: " + (valueCount != null ? valueCount : "null"));

                    // Handle potential null values in metricName
                    String sanitizedMetricName = (metricStreamData.getMetricName() != null)
                            ? metricStreamData.getMetricName().replaceAll("[^a-zA-Z0-9]", "_")
                            : "Unknown_Metric";

                    context.getLogger().log("Sanitized Metric Name: " + sanitizedMetricName);

                    List<Gauge> gauges = createGauges(metricStreamData, context);

                    // Push the metrics to Prometheus
                    pushMetricsToPrometheus(gauges);

                    // Add the record to the response
                    KinesisFirehoseResponse.Record responseRecord = new KinesisFirehoseResponse.Record();
                    responseRecord.setRecordId(record.getRecordId());
                    responseRecord.setResult(KinesisFirehoseResponse.Result.Ok);
                    responseRecords.add(responseRecord);
                } catch (Exception e) {
                    context.getLogger().log("Error processing record: " + e.getMessage());
                    context.getLogger().log("Exception: " + e.toString()); // Log the exception details
                }
            }
        }



        response.setRecords(responseRecords);
        return response;
    }

    public String createMetricNameLabel(String name, Values value) {

        return sanitize(name) + "_" + value.name().toLowerCase();

    }

    public String createNamespaceLabel(String input) {

        // Implementation of createNamespaceLabel method

        return input.replaceAll("[^a-zA-Z0-9]", "_");

    }

    public Map<String, String> createDimensionLabels(Map<String, String> dimensions) {

        Map<String, String> sanitizedDimensions = new HashMap<>();

        for (Map.Entry<String, String> entry : dimensions.entrySet()) {

            sanitizedDimensions.put(sanitize(entry.getKey()), sanitize(entry.getValue()));

        }

        return sanitizedDimensions;

    }

    public Map<String, String> createCustomLabels(String labels) {

        Map<String, String> labelMap = new HashMap<>();

        String[] pairs = labels.split(",");

        for (String pair : pairs) {

            String[] keyValue = pair.split(":");

            if (keyValue.length == 2) {

                labelMap.put(keyValue[0], keyValue[1]);

            }

        }

        return labelMap;

    }

    private List<Gauge> createGauges(MetricStreamData metricStreamData, Context context) {
        List<Gauge> gauges = new ArrayList<>();
        String sanitizedMetricName = sanitize(metricStreamData.getMetricName());

        // Log the sanitized metric name
        context.getLogger().log("Sanitized Metric Name: " + sanitizedMetricName);

        Gauge countGauge = Gauge.build()
                .name(sanitizedMetricName + "_count")
                .help("Count of " + sanitizedMetricName)
                .register();
        countGauge.set(metricStreamData.getValue().getCount());
        gauges.add(countGauge);


        return gauges;
    }

    private void pushMetricsToPrometheus(List<Gauge> gauges) throws Exception {
        String prometheusRemoteWriteUrl = System.getenv("PROMETHEUS_REMOTE_WRITE_URL");
        String awsRegion = System.getenv("AWS_REGION");
        String awsAmpRoleArn = System.getenv("AWS_AMP_ROLE_ARN");

        HttpClient client = HttpClient.newHttpClient(); // Single HttpClient instance

        for (Gauge gauge : gauges) {
            // Serialize gauge metrics to text format
            StringWriter writer = new StringWriter();
            Enumeration<Collector.MetricFamilySamples> mfs = Collections.enumeration(gauge.collect());
            TextFormat.write004(writer, mfs);
            byte[] body = writer.toString().getBytes();

            // AWS Credentials Provider
            AwsCredentialsProvider credentialsProvider;
            if (awsAmpRoleArn != null && !awsAmpRoleArn.isEmpty()) {
                credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                        .refreshRequest(AssumeRoleRequest.builder()
                                .roleArn(awsAmpRoleArn)
                                .roleSessionName("prometheus-session")
                                .build())
                        .build();
            } else {
                credentialsProvider = DefaultCredentialsProvider.create();
            }

            // Build the unsigned SDK HTTP request
            SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                    .uri(URI.create(prometheusRemoteWriteUrl))
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/x-protobuf")
                    .putHeader("Content-Encoding", "snappy")
                    .putHeader("X-Prometheus-Remote-Write-Version", "0.1.0")
                    .contentStreamProvider(() -> new ByteArrayInputStream(body)) // Stream recreated for each request
                    .build();

            // Sign the request
            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .signingRegion(Region.of(awsRegion))
                    .signingName("aps")
                    .awsCredentials(credentialsProvider.resolveCredentials())
                    .build();
            Aws4Signer signer = Aws4Signer.create();
            SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);

            // Build the HTTP request from the signed SDK request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(signedRequest.getUri())
                    .method("POST", HttpRequest.BodyPublishers.ofByteArray(body))
                    .headers(signedRequest.headers().entrySet().stream()
                            .flatMap(entry -> entry.getValue().stream().map(value -> Map.entry(entry.getKey(), value)))
                            .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                            .toArray(String[]::new))
                    .build();

            // Send the request
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Check for errors
            if (response.statusCode() != 200) {
                throw new RuntimeException("Request to AMP failed with status: " + response.statusCode() +
                        ", body: " + response.body());
            }
        }
    }

    public String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    // MetricStreamData class
    public static class MetricStreamData {
        private String metricStreamName;
        private String accountID;
        private String region;
        private String namespace;
        private String metricName;
        private Map<String, Object> dimensions;
        private long timestamp;
        private Value value;
        private String unit;

        // Getters and setters
        public String getMetricStreamName() { return metricStreamName; }
        public void setMetricStreamName(String metricStreamName) { this.metricStreamName = metricStreamName; }

        public String getAccountID() { return accountID; }
        public void setAccountID(String accountID) { this.accountID = accountID; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public Map<String, Object> getDimensions() { return dimensions; }
        public void setDimensions(Map<String, Object> dimensions) { this.dimensions = dimensions; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public Value getValue() { return value; }
        public void setValue(Value value) { this.value = value; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    // Value class
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

        public double getSum() {
            return sum;
        }

        public void setSum(double sum) {
            this.sum = sum;
        }

        public double getMax() {
            return max;
        }

        public void setMax(double max) {
            this.max = max;
        }

        public double getMin() {
            return min;
        }

        public void setMin(double min) {
            this.min = min;
        }
    }

    // KinesisFirehoseResponse class
    public static class KinesisFirehoseResponse {
        private List<Record> records;

        public List<Record> getRecords() {
            return records;
        }

        public void setRecords(List<Record> records) {
            this.records = records;
        }

        public static class Record {
            private String recordId;
            private Result result;

            public String getRecordId() {
                return recordId;
            }

            public void setRecordId(String recordId) {
                this.recordId = recordId;
            }

            public Result getResult() {
                return result;
            }

            public void setResult(Result result) {
                this.result = result;
            }
        }

        public enum Result {
            Ok, Dropped, ProcessingFailed
        }
    }

    public enum Values {
        COUNT, SUM, MAX, MIN
    }
}
package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xerial.snappy.Snappy;
import prometheus.Remote;
import prometheus.Types;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class LambdaHandler implements RequestHandler<KinesisFirehoseEvent, LambdaHandler.KinesisFirehoseResponse> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_METRIC_NAME_LENGTH = 200;
    private static final int MAX_DIMENSION_VALUE_LENGTH = 100;
    private SigV4RequestHandler sigV4Handler;

    
    @Override
    public KinesisFirehoseResponse handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {
        if (sigV4Handler == null) {
            initSigV4Handler();
        }

        KinesisFirehoseResponse response = new KinesisFirehoseResponse();
        List<KinesisFirehoseResponse.Record> responseRecords = new ArrayList<>();
        List<MetricStreamData> batchMetrics = new ArrayList<>();

        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            try {
                MetricStreamData metricData = parseAndValidateRecord(record);
                batchMetrics.add(metricData);
                
                if (batchMetrics.size() >= MAX_BATCH_SIZE) {
                    sendMetricBatch(batchMetrics, context);
                    batchMetrics.clear();
                }
                
                responseRecords.add(createSuccessResponse(record.getRecordId()));
            } catch (Exception e) {
                context.getLogger().log("Error processing record: " + e.getMessage());
                responseRecords.add(createFailureResponse(record.getRecordId()));
            }
        }

        if (!batchMetrics.isEmpty()) {
            try {
                sendMetricBatch(batchMetrics, context);
            } catch (Exception e) {
                context.getLogger().log("Error sending final batch: " + e.getMessage());
            }
        }

        response.setRecords(responseRecords);
        return response;
    }

    private void initSigV4Handler() {
        String region = getRequiredEnvVar("AWS_REGION");
        String roleArn = getRequiredEnvVar("AWS_AMP_ROLE_ARN");
        sigV4Handler = new SigV4RequestHandler(region, roleArn);
    }

    private MetricStreamData parseAndValidateRecord(KinesisFirehoseEvent.Record record) throws Exception {
        String data = new String(record.getData().array(), StandardCharsets.UTF_8);
        MetricStreamData metricData = objectMapper.readValue(data, MetricStreamData.class);
        validateMetricData(metricData);
        return metricData;
    }

    private void validateMetricData(MetricStreamData metricData) {
        if (metricData.getMetricName() == null || metricData.getMetricName().length() > MAX_METRIC_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid metric name");
        }

        if (metricData.getDimensions() != null) {
            for (Map.Entry<String, String> dim : metricData.getDimensions().entrySet()) {
                if (dim.getValue().length() > MAX_DIMENSION_VALUE_LENGTH) {
                    throw new IllegalArgumentException("Dimension value too long: " + dim.getKey());
                }
            }
        }
    }

    private void sendMetricBatch(List<MetricStreamData> metrics, Context context) throws Exception {
        byte[] prometheusData = PrometheusFormatter.formatMetricBatch(metrics);
        String prometheusUrl = getRequiredEnvVar("PROMETHEUS_REMOTE_WRITE_URL");
        String roleArn = getRequiredEnvVar("AWS_AMP_ROLE_ARN");
        AwsCredentialsProvider credsProvider = sigV4Handler.getCredentialsProvider(roleArn);
        AwsCredentials creds = credsProvider.resolveCredentials();
        Sigv4Signer signer = new Sigv4Signer(
                creds.accessKeyId(),
                creds.secretAccessKey(),
                "x-api-key",
                URI.create(prometheusUrl));
        String body = signer.sendRequest(new String(prometheusData));
        HttpExecuteResponse response = sigV4Handler.send(URI.create(prometheusUrl), prometheusData);
        handleResponse(response);
    }

    private void handleResponse(HttpExecuteResponse response) throws Exception {
        int statusCode = response.httpResponse().statusCode();
        if (statusCode != 200) {
            String responseBody = new String(response.responseBody()
                    .orElseThrow(() -> new RuntimeException("No response body"))
                    .readAllBytes());
            throw new RuntimeException("Request failed with status: " + statusCode + ", body: " + responseBody);
        }
    }

    private String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variable missing: " + name);
        }
        return value;
    }

    private KinesisFirehoseResponse.Record createSuccessResponse(String recordId) {
        KinesisFirehoseResponse.Record response = new KinesisFirehoseResponse.Record();
        response.setRecordId(recordId);
        response.setResult(KinesisFirehoseResponse.Result.Ok);
        return response;
    }

    private KinesisFirehoseResponse.Record createFailureResponse(String recordId) {
        KinesisFirehoseResponse.Record response = new KinesisFirehoseResponse.Record();
        response.setRecordId(recordId);
        response.setResult(KinesisFirehoseResponse.Result.ProcessingFailed);
        return response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetricStreamData {
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
        public String getMetricStreamName() { return metricStreamName; }
        public void setMetricStreamName(String metricStreamName) { this.metricStreamName = metricStreamName; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public Value getValue() { return value; }
        public void setValue(Value value) { this.value = value; }

        public Map<String, String> getDimensions() { return dimensions; }
        public void setDimensions(Map<String, String> dimensions) { this.dimensions = dimensions; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    public static class Value {
        private double count;
        private double sum;
        private double max;
        private double min;
        public double getCount() { return count; }
        public void setCount(double count) { this.count = count; }
    }

    public static class KinesisFirehoseResponse {
        private List<Record> records;

        public List<Record> getRecords() { return records; }
        public void setRecords(List<Record> records) { this.records = records; }

        public static class Record {
            private String recordId;
            private Result result;

            public String getRecordId() { return recordId; }
            public void setRecordId(String recordId) { this.recordId = recordId; }

            public Result getResult() { return result; }
            public void setResult(Result result) { this.result = result; }
        }

        public enum Result {
            Ok, Dropped, ProcessingFailed
        }
    }
}

class PrometheusFormatter {
    public static byte[] formatMetricBatch(List<LambdaHandler.MetricStreamData> metrics) {

        Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
        for (LambdaHandler.MetricStreamData metric : metrics) {
            if (metric.getDimensions() != null) {
                for (Map.Entry<String, String> dimension : metric.getDimensions().entrySet()) {
                    timeSeries.addLabels(Types.Label.newBuilder()
                        .setName(sanitize(dimension.getKey()))
                        .setValue(dimension.getValue().replace("\"", "\\\""))
                        .build());
                }
            }
            timeSeries.addLabels(Types.Label.newBuilder()
                .setName("metric_stream")
                .setValue(metric.getMetricStreamName())
            );
            timeSeries.addSamples(Types.Sample.newBuilder()
                    .setTimestamp(metric.getTimestamp())
                    .setValue(metric.getValue().getCount())
                    .build());
        }

//        Types.MetricMetadata.Builder metricMetadata = Types.MetricMetadata.newBuilder()
//                .setUnit()
        // Create a WriteRequest
        Remote.WriteRequest writeRequest = Remote.WriteRequest.newBuilder()
                .addTimeseries(timeSeries.build())
                .build();

        // Serialize to Protobuf
        byte[] protobufData = writeRequest.toByteArray();



        return protobufData;
    }

    private static void appendMetric(Types.TimeSeries.Builder timeSeries, LambdaHandler.MetricStreamData metric, long timestamp) {


    }

    private static String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}


class SigV4RequestHandler {
    private static final String SIGNING_NAME = "aps";
    private static final SdkHttpClient httpClient = ApacheHttpClient.builder()
            .socketTimeout(Duration.ofSeconds(5))
            .connectionTimeout(Duration.ofSeconds(2))
            .build();

    private final Region region;
    private final AwsCredentialsProvider credentialsProvider;

    public SigV4RequestHandler(String region, String roleArn) {
        this.region = Region.of(region);
        this.credentialsProvider = getCredentialsProvider(roleArn);
    }

    public HttpExecuteResponse send(URI uri, byte[] payload) throws Exception {
        byte[] compressedData = Snappy.compress(payload);
        AwsSessionCredentials credentials = (AwsSessionCredentials)credentialsProvider.resolveCredentials();

        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .uri(uri)
                .method(SdkHttpMethod.POST)
                .putHeader("host", uri.getHost())
                .putHeader("x-amz-date", formatAmzDate(Instant.now()))
                .putHeader("x-amz-security-token", credentials.sessionToken())
                .putHeader("x-amz-content-sha256", calculateSha256(compressedData))
                .putHeader("content-encoding", "snappy")
                .putHeader("content-type", "application/x-protobuf")
                .putHeader("x-prometheus-remote-write-version", "0.1.0")
                .contentStreamProvider(() ->
                        AbortableInputStream.create(new ByteArrayInputStream(compressedData)));

        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .signingName(SIGNING_NAME)
                .signingRegion(region)
                .awsCredentials(credentials)
                .timeOffset(0)
                .build();

        SdkHttpFullRequest signedRequest = Aws4Signer.create()
                .sign(requestBuilder.build(), signerParams);

        return httpClient.prepareRequest(HttpExecuteRequest.builder()
                        .request(signedRequest)
                        .build())
                .call();
    }

    private String formatAmzDate(Instant now) {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(now);
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate SHA-256", e);
        }
    }

    public AwsCredentialsProvider getCredentialsProvider(String roleArn) {
        if (roleArn != null && !roleArn.isEmpty()) {
            StsClient stsClient = StsClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            return StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(stsClient)
                    .refreshRequest(AssumeRoleRequest.builder()
                            .roleArn(roleArn)
                            .roleSessionName("prometheus-session")
                            .build())
                    .build();
        }
        return DefaultCredentialsProvider.create();
    }
}
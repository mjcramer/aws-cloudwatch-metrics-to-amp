package com.example;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.Gauge;
import io.prometheus.client.Collector;
import org.xerial.snappy.Snappy;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class DynamoDBMetricsLambda implements RequestHandler<KinesisFirehoseEvent, DynamoDBMetricsLambda.KinesisFirehoseResponse> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public KinesisFirehoseResponse handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {
        KinesisFirehoseResponse response = new KinesisFirehoseResponse();
        List<KinesisFirehoseResponse.Record> responseRecords = new ArrayList<>();
        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            String data = new String(record.getData().array(), StandardCharsets.UTF_8);
            context.getLogger().log("Decoded data: " + data);
            String[] splitRecord = data.split("\n");
            for (String line : splitRecord) {
                if (line.trim().isEmpty()) {
                    context.getLogger().log("Empty or invalid record skipped.");
                    continue;
                }
                try {
                    context.getLogger().log("Processing record: " + line);
                    MetricStreamData metricStreamData = objectMapper.readValue(line, MetricStreamData.class);
                    List<Gauge> gauges = createGauges(metricStreamData);
                    pushMetricsToPrometheus(gauges, context);
                    KinesisFirehoseResponse.Record responseRecord = new KinesisFirehoseResponse.Record();
                    responseRecord.setRecordId(record.getRecordId());
                    responseRecord.setResult(KinesisFirehoseResponse.Result.Ok);
                    responseRecords.add(responseRecord);
                } catch (Exception e) {
                    context.getLogger().log("Error processing record: " + e.getMessage());
                    KinesisFirehoseResponse.Record responseRecord = new KinesisFirehoseResponse.Record();
                    responseRecord.setRecordId(record.getRecordId());
                    responseRecord.setResult(KinesisFirehoseResponse.Result.ProcessingFailed);
                    responseRecords.add(responseRecord);
                }
            }
        }
        response.setRecords(responseRecords);
        return response;
    }
    private void pushMetricsToPrometheus(List<Gauge> gauges, Context context) throws Exception {
        final String prometheusRemoteWriteUrl = System.getenv("PROMETHEUS_REMOTE_WRITE_URL");
        final String awsRegion = System.getenv("AWS_REGION");
        final String awsAmpRoleArn = System.getenv("AWS_AMP_ROLE_ARN");
        final AwsCredentialsProvider credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .stsClient(StsClient.builder().region(Region.of(awsRegion)).build())
                .refreshRequest(builder -> builder.roleArn(awsAmpRoleArn).roleSessionName("aps"))
                .build();
        final Aws4Signer signer = Aws4Signer.create();
        final Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentialsProvider.resolveCredentials())
                .signingName("aps")
                .signingRegion(Region.of(awsRegion))
                .build();
        for (final Gauge gauge : gauges) {
            final StringWriter writer = new StringWriter();
            final Enumeration<Collector.MetricFamilySamples> mfs = Collections.enumeration(gauge.collect());
            TextFormat.write004(writer, mfs);
            final String serializedMetrics = writer.toString();
            final byte[] body = serializedMetrics.getBytes(StandardCharsets.UTF_8);
            final byte[] compressedBody;
            try {
                compressedBody = Snappy.compress(body);
            } catch (IOException e) {
                context.getLogger().log("Snappy compression failed: " + e.getMessage());
                throw new RuntimeException("Failed to compress the metrics using Snappy", e);
            }
            context.getLogger().log("Serialized Metrics: " + serializedMetrics);
            final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
            final AwsCredentials awsCredentials = credentialsProvider.resolveCredentials();
            final String sessionToken;
            if (awsCredentials instanceof AwsSessionCredentials) {
                sessionToken = ((AwsSessionCredentials) awsCredentials).sessionToken();
            } else {
                sessionToken = "";
            }
            final SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                    .uri(URI.create(prometheusRemoteWriteUrl))
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/x-protobuf")
                    .putHeader("Content-Encoding", "snappy")
                    .putHeader("X-Prometheus-Remote-Write-Version", "0.1.0")
                    .putHeader("X-Amz-Security-Token", sessionToken)
                    .contentStreamProvider(() -> new ByteArrayInputStream(compressedBody))
                    .build();
            final HttpExecuteRequest httpExecuteRequest = HttpExecuteRequest.builder()
                    .request(sdkRequest)
                    .contentStreamProvider(() -> new ByteArrayInputStream(compressedBody))
                    .build();
            final HttpExecuteResponse response = httpClient.prepareRequest(httpExecuteRequest).call();
            if (response.httpResponse().statusCode() != 200) {
                final String responseBody = response.responseBody().map(responseBodyStream -> {
                    try {
                        return new String(responseBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return "Unable to read response body";
                    }
                }).orElse("No response body");
                throw new RuntimeException("Request to AMP failed with status: " + response.httpResponse().statusCode() +
                        ", body: " + responseBody);
            }
        }
    }
    List<Gauge> createGauges(MetricStreamData metricStreamData) {
        List<Gauge> gauges = new ArrayList<>();
        String sanitizedMetricName = sanitize(metricStreamData.getMetricName());
        Gauge countGauge = Gauge.build()
                .name(sanitizedMetricName + "_count")
                .help("Count of " + sanitizedMetricName)
                .register();
        countGauge.set(metricStreamData.getValue().getCount());
        gauges.add(countGauge);
        return gauges;
    }
    public String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_");
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetricStreamData {
        private String metricStreamName;
        private String metricName;
        private Value value;
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
    }
    public static class Value {
        private double count;
        public double getCount() {
            return count;
        }
        public void setCount(double count) {
            this.count = count;
        }
    }
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
}

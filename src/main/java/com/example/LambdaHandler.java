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
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LambdaHandler implements RequestHandler<KinesisFirehoseEvent, LambdaHandler.KinesisFirehoseResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public KinesisFirehoseResponse handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {
        KinesisFirehoseResponse response = new KinesisFirehoseResponse();
        List<KinesisFirehoseResponse.Record> responseRecords = new ArrayList<>();

        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            String data = new String(record.getData().array());
            context.getLogger().log("Decoded data: " + data);

            String[] splitRecord = data.split("\n");
            for (String x : splitRecord) {
                if (x.trim().isEmpty()) {
                    context.getLogger().log("Empty or invalid record skipped.");
                    continue;
                }

                try {
                    context.getLogger().log("Processing record: " + x);
                    MetricStreamData metricStreamData = objectMapper.readValue(x, MetricStreamData.class);
                    List<Gauge> gauges = createGauges(metricStreamData, context);
                    pushMetricsToPrometheus(gauges);

                    KinesisFirehoseResponse.Record responseRecord = new KinesisFirehoseResponse.Record();
                    responseRecord.setRecordId(record.getRecordId());
                    responseRecord.setResult(KinesisFirehoseResponse.Result.Ok);
                    responseRecords.add(responseRecord);
                } catch (Exception e) {
                    context.getLogger().log("Error processing record: " + e.getMessage());
                }
            }
        }

        response.setRecords(responseRecords);
        return response;
    }

    private void pushMetricsToPrometheus(List<Gauge> gauges) throws Exception {
        String prometheusRemoteWriteUrl = System.getenv("PROMETHEUS_REMOTE_WRITE_URL");
        String awsRegion = System.getenv("AWS_REGION");
        String awsAmpRoleArn = System.getenv("AWS_AMP_ROLE_ARN");

        String sessionToken = getAuthorizationToken(awsAmpRoleArn, "aps", awsRegion);

        AwsCredentialsProvider credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                .stsClient(StsClient.create())
                .refreshRequest(builder -> builder.roleArn(awsAmpRoleArn)
                                                  .roleSessionName("aps"))
                .build();
        AwsCredentials creds = credentialsProvider.resolveCredentials();

        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentialsProvider.resolveCredentials())
                .signingName("aps")
                .signingRegion(Region.of(awsRegion))
                .build();

        for (Gauge gauge : gauges) {
            StringWriter writer = new StringWriter();
            Enumeration<Collector.MetricFamilySamples> mfs = Collections.enumeration(gauge.collect());
            TextFormat.write004(writer, mfs);

            String serializedMetrics = writer.toString();
            byte[] body = serializedMetrics.getBytes(StandardCharsets.UTF_8);
            byte[] compressedBody = Snappy.compress(body);

            System.out.println("Serialized Metrics: " + serializedMetrics);
            System.out.println("Compressed Body (Hex): " + Arrays.toString(compressedBody));

            SdkHttpClient httpClient = ApacheHttpClient.create();

            SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                    .uri(URI.create(prometheusRemoteWriteUrl))
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/x-protobuf")
                    .putHeader("Content-Encoding", "snappy")
                    .putHeader("X-Prometheus-Remote-Write-Version", "0.1.0")
                    .putHeader("X-Amz-Security-Token", sessionToken)
                    .contentStreamProvider(() -> new ByteArrayInputStream(compressedBody))
                    .build();

            SignedRequest signedRequest = signer.sign(r -> r.identity(creds)
                    .request(sdkRequest)
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "aps")
                    .putProperty(AwsV4HttpSigner.REGION_NAME, awsRegion));

            HttpExecuteRequest httpExecuteRequest = HttpExecuteRequest.builder()
                    .request(signedRequest.request())
                    .contentStreamProvider(signedRequest.payload().orElse(null))
                    .build();

            HttpExecuteResponse response = httpClient.prepareRequest(httpExecuteRequest).call();

            if (response.httpResponse().statusCode() != 200) {
                String responseBody = response.responseBody().map(responseBodyStream -> {
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

    private String getAuthorizationToken(String roleArn, String sessionName, String region) {
        StsClient stsClient = StsClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();

        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(sessionName)
                .build();

        AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);
        System.out.println("AssumeRoleResponse: " + assumeRoleResponse);

        return assumeRoleResponse.credentials().sessionToken();
    }

    private List<Gauge> createGauges(MetricStreamData metricStreamData, Context context) {
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

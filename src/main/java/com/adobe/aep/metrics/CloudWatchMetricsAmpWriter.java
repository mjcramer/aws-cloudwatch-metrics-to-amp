package com.adobe.aep.metrics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;
import prometheus.Remote;
import prometheus.Types;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.adobe.aep.metrics.AwsV4SigningUtils.*;


public class CloudWatchMetricsAmpWriter implements RequestHandler<KinesisFirehoseEvent, FirehoseEventProcessingResult> {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_METRIC_NAME_LENGTH = 200;
    private static final int MAX_DIMENSION_VALUE_LENGTH = 100;

    private static final String METHOD = "POST";
    private static final String SERVICE = "aps";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String CONTENT_TYPE = "application/x-protobuf";
    private static final String CONTENT_ENCODING = "snappy";
    private static final String API_KEY = "";

    private final String awsRegion;
    private final String ampWorkspaceId;
    private final String restApiHost;
    private final String restApiPath;
    private final String restApiEndpoint;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final AwsSessionCredentials credentials;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
    private final boolean signedRequests;
    private final String metricFormat;

    private final Logger logger = LoggerFactory.getLogger(CloudWatchMetricsAmpWriter.class);

    public CloudWatchMetricsAmpWriter() {
        this.awsRegion = getEnvVar("REGION");
        this.ampWorkspaceId = getEnvVar("WORKSPACE_ID");
        this.restApiHost = String.format("aps-workspaces.%s.amazonaws.com", awsRegion);
        this.restApiPath = String.format("/workspaces/%s/api/v1/remote_write", ampWorkspaceId);
        this.restApiEndpoint = String.format("https://%s%s", restApiHost, restApiPath);
        this.credentials = (AwsSessionCredentials) DefaultCredentialsProvider.create().resolveCredentials();
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.signedRequests = Boolean.parseBoolean(getEnvVar("SIGNED_REQUESTS", "true"));
        this.metricFormat = getEnvVar("METRIC_FORMAT", "json");
    }

    @Override
    public FirehoseEventProcessingResult handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {

        List<FirehoseEventProcessingResult.Record> responseRecords = new ArrayList<>();
        List<CloudWatchStreamMetric> batchMetrics = new ArrayList<>();
        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            context.getLogger().log(String.format("Received record %s", record.getRecordId()));
            String recordData = new String(record.getData().array(), StandardCharsets.UTF_8);
            context.getLogger().log(String.format("Record data: %s", recordData));

            try {
                boolean success = true;
                ArrayList<String> tally = new ArrayList<>();
                for (String metricData : recordData.split("\n")) {
                    CloudWatchStreamMetric metric = objectMapper.readValue(metricData, CloudWatchStreamMetric.class);
                    tally.add(metric.metricName);
                    batchMetrics.add(metric);
                    if (batchMetrics.size() >= MAX_BATCH_SIZE) {
                        context.getLogger().log(String.format("Max batch size reached, sending %d metrics", batchMetrics.size()));
                        success = sendMetricBatch(batchMetrics, context);
                        if (!success) {
                            break;
                        }
                        batchMetrics.clear();
                    }
                }
                context.getLogger().log(String.format("Parsed record for metrics %s", String.join(", ", tally)));
                if (success) {
                    context.getLogger().log(String.format("Successfully processed record %s", record.getRecordId()));
                    responseRecords.add(FirehoseEventProcessingResult.createSuccessResult(record.getRecordId(), recordData));
                } else {
                    context.getLogger().log(String.format("Failed to send record %s", record.getRecordId()));
                    responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId(), "Batch failed to write."));
                }
            } catch (JsonProcessingException e) {
                context.getLogger().log("Error processing record: " + e.getMessage());
                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId(), e.getMessage()));
            } catch (IOException e) {
                context.getLogger().log("Error sending data to amp: " + e.getMessage());
                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId(), e.getMessage()));
            } catch (NoSuchAlgorithmException e) {
                context.getLogger().log("Error encoding data for sending: " + e.getMessage());
                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId(), e.getMessage()));
            }
        }

        if (!batchMetrics.isEmpty()) {
            try {
                context.getLogger().log(String.format("Processed %d records, sending %d metric stream items", responseRecords.size(), batchMetrics.size()));
                if (sendMetricBatch(batchMetrics, context)) {
                    context.getLogger().log("Successfully sent final batch.");
                } else {
                    context.getLogger().log("Failed to send final batch.");
                }
            } catch (Exception e) {
                // TODO: Need better handling here if we can't reach prometheus
                context.getLogger().log("Error processing final batch: " + e.getMessage());
            }
        }

        FirehoseEventProcessingResult response = new FirehoseEventProcessingResult(responseRecords);
        try {
            context.getLogger().log(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            context.getLogger().log("Can't print response object");
        }
        return response;
    }

    private String getEnvVar(String name) {
        return this.getEnvVar(name, null);
    }

    private String getEnvVar(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            if (defaultValue == null) {
                throw new IllegalStateException("Required environment variable missing: " + name);
            } else {
                return defaultValue;
            }
        }
        return value;
    }

    private boolean sendMetricBatch(List<CloudWatchStreamMetric> metrics, Context context)
            throws IOException, NoSuchAlgorithmException {

        // Construct Protobuf payload
        byte[] prometheusData = serializeToProto(metrics);
        byte[] compressedData = Snappy.compress(prometheusData);

        String amzDate = dateFormat.format(new Date());
        String dateStamp = amzDate.substring(0, 8);

        // Create the canonical request
        String canonicalUri = restApiPath;
        String canonicalQuerystring = "";

        String payloadHash = sha256Hex(compressedData);
        // CanonicalHeaders:
        // - "For the purpose of calculating an authorization signature, only the host and any x-amz-* headers
        //      are required; however, in order to prevent data tampering, you should consider including all
        //      the headers in the signature calculation."
        // - "must appear in alphabetical order" / "sorted by header name"
        // - "lowercase with values trimmed"
        // - "If the Content-Type header is present in the request, you must add it to the CanonicalHeaders list. "
        // - The last header is also \n terminated
        String canonicalHeaders =
                "content-type:" + CONTENT_TYPE + "\n" +
                        "host:" + restApiHost + "\n" +
                        "x-amz-content-sha256:" + payloadHash + "\n" +
                        "x-amz-date:" + amzDate + "\n" +
                        "x-api-key:" + API_KEY + "\n";  // terminate last with \n
        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date;x-api-key";

        String canonicalRequest =
                METHOD + "\n" +
                        canonicalUri + "\n" +
                        canonicalQuerystring + "\n" +
                        canonicalHeaders + "\n" +
                        signedHeaders + "\n" +
                        payloadHash;

        // System.out.printf("Canonical Request: '%s'\n", canonicalRequest.replace("\n", "\\n"));

        String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, awsRegion, SERVICE);
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign =
                ALGORITHM + "\n" +
                        amzDate + "\n" +
                        credentialScope + "\n" +
                        hashedCanonicalRequest;

        // System.out.printf("String to Sign: '%s'\n", stringToSign);

        byte[] signingKey = getSignatureKey(credentials.secretAccessKey(), dateStamp, awsRegion, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // Add signing information to the request
        String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                ALGORITHM, credentials.accessKeyId(), credentialScope, signedHeaders, signature);

        System.out.printf("Sending %d bytes payload to prometheus at %s\n", compressedData.length, restApiEndpoint);

        HttpPost post = new HttpPost(URI.create(restApiEndpoint));
        post.setHeader("host", restApiHost);
        post.setHeader("Content-Encoding", CONTENT_ENCODING);
        post.setHeader("Content-Type", CONTENT_TYPE);
        post.setHeader("x-amz-date", amzDate);
        post.setHeader("x-amz-security-token", credentials.sessionToken());
        post.setHeader("x-amz-content-sha256", payloadHash);
        post.setHeader("x-prometheus-remote-write-version", "0.1.0");
        post.setHeader("Authorization", authorizationHeader);

        // Attach compressed data as the request body
        post.setEntity(new ByteArrayEntity(compressedData));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                System.out.printf("Returned status code %s: %s\n", statusCode,
                        new String(response.getEntity().getContent().readAllBytes(), Charset.defaultCharset())
                );
                return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private byte[] serializeToProto(List<CloudWatchStreamMetric> metrics) {
        Remote.WriteRequest.Builder writeRequest = Remote.WriteRequest.newBuilder();

        Map<String, List<CloudWatchStreamMetric>> metricsGroupedByName = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.metricName));
        metricsGroupedByName.forEach((metricName, groupedMetrics) -> {
            System.out.printf("Aggregating %d metrics for '%s'\n", groupedMetrics.size(), metricName);

            Map<Map<String, String>, List<CloudWatchStreamMetric>> groupedMetricsGroupedByDimensions =
                    groupedMetrics.stream().collect(Collectors.groupingBy(m -> m.dimensions));
            groupedMetricsGroupedByDimensions.forEach((dimensions, nestedGroupedMetrics) -> {
                Pair<Types.TimeSeries, Types.MetricMetadata> pair = this.getWriteRequestPair(metricName, dimensions, nestedGroupedMetrics);
                System.out.printf("Write record for %s, adding %d samples of type %s\n", metricName, pair.first().getSamplesCount(), pair.second().getUnit());
                writeRequest.addTimeseries(pair.first());
                writeRequest.addMetadata(pair.second());
            });
        });

        // Serialize to Protobuf
        return writeRequest.build().toByteArray();
    }

    private Pair<Types.TimeSeries, Types.MetricMetadata> getWriteRequestPair(String metricName, Map<String, String> dimensions, List<CloudWatchStreamMetric> metricsValues) {
        Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
        Types.MetricMetadata.Builder metaData = Types.MetricMetadata.newBuilder();
        if (dimensions != null) {
            for (Map.Entry<String, String> dimension : dimensions.entrySet()) {
                timeSeries.addLabels(Types.Label.newBuilder()
                        .setName(sanitize(dimension.getKey()))
                        .setValue(dimension.getValue())
                        .build());
                System.out.printf("Adding dimension %s with value %s for metric '%s'\n", dimension.getKey(), dimension.getValue(), metricName);
            }
        }
        timeSeries.addLabels(Types.Label.newBuilder()
                .setName("__name__")
                .setValue(sanitize(metricName))
        );
        for (CloudWatchStreamMetric metric : metricsValues) {
            Types.Sample.Builder sample = Types.Sample.newBuilder();
            sample.setTimestamp(metric.timestamp);
            if (metric.unit != null) {
                metaData.setUnit(metric.unit);
                if (metric.unit.equals("Count")) {
                    metaData.setType(Types.MetricMetadata.MetricType.COUNTER);
                    sample.setValue(metric.value.count);
                }
                // TODO: What about other types?
            }
            System.out.printf("Adding sample %f at timestamp %d for metric '%s'\n", sample.getValue(), sample.getTimestamp(), metricName);
            timeSeries.addSamples(sample.build());
            metaData.setMetricFamilyName(sanitize(metric.namespace));
        }
        return new Pair<>(timeSeries.build(), metaData.build());
    }

    private String sanitize (String input){
            return input.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        }
    }

    record Pair<F, S>(F first, S second) {
    }


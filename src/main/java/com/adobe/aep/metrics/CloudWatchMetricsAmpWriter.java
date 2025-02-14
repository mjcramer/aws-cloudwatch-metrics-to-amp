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

    private final Logger logger = LoggerFactory.getLogger(CloudWatchMetricsAmpWriter.class);


    public CloudWatchMetricsAmpWriter() {
        this(false);
    }

    public CloudWatchMetricsAmpWriter(Boolean testMode) {
        if (testMode) {
            this.awsRegion = null;
            this.ampWorkspaceId = null;
            this.restApiHost = "localhost:9090";
            this.restApiPath = "/api/v1/write";
            this.restApiEndpoint = String.format("http://%s%s", restApiHost, restApiPath);
            this.credentials = null;
            this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        } else {
            this.awsRegion = getRequiredEnvVar("REGION");
            this.ampWorkspaceId = getRequiredEnvVar("WORKSPACE_ID");
            this.restApiHost = String.format("aps-workspaces.%s.amazonaws.com", awsRegion);
            this.restApiPath = String.format("/workspaces/%s/api/v1/remote_write", ampWorkspaceId);
            this.restApiEndpoint = String.format("https://%s%s", restApiHost, restApiPath);
            this.credentials = (AwsSessionCredentials) DefaultCredentialsProvider.create().resolveCredentials();
            this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        }
    }

    @Override
    public FirehoseEventProcessingResult handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {
//        logger.debug("Lambda function invoked with event ");
//        logger.debug("Event: {}", firehoseEvent);
//
//        LambdaLogger lambdaLogger = context.getLogger();
//        lambdaLogger.log("Lambda function logging using context.getLogger()");

        List<FirehoseEventProcessingResult.Record> responseRecords = new ArrayList<>();
        List<CloudWatchMetric> batchMetrics = new ArrayList<>();
        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
            context.getLogger().log(String.format("Received record %s", record.getRecordId()));
            String recordData = new String(record.getData().array(), StandardCharsets.UTF_8);
            context.getLogger().log(String.format("Record data: %s", recordData));
            try {
                CloudWatchMetric metric = objectMapper.readValue(recordData, CloudWatchMetric.class);
                for (CloudWatchMetric metric : metricRecord.detail.metrics) {
                    context.getLogger().log(String.format("Parsed record in metric %s", metric.metricName));
                    batchMetrics.add(metric);
                    if (batchMetrics.size() >= MAX_BATCH_SIZE) {
                        sendMetricBatch(batchMetrics, context);
                        batchMetrics.clear();
                    }
                }
                responseRecords.add(FirehoseEventProcessingResult.createSuccessResult(record.getRecordId()));
                // TODO: Code smell here, we shan't be catching all Exceptions and swallowing them
            }
            catch (JsonProcessingException e) {
                context.getLogger().log("Error processing record: " + e.getMessage());
                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId()));
            }
//            catch (IOException e) {
//                context.getLogger().log("Error sending data to amp: " + e.getMessage());
//                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId()));
//            }
//            catch (NoSuchAlgorithmException e) {
//                context.getLogger().log("Error encoding data for sending: " + e.getMessage());
//                responseRecords.add(FirehoseEventProcessingResult.createFailureResult(record.getRecordId()));
//            }
        }

        if (!batchMetrics.isEmpty()) {
            try {
                sendMetricBatch(batchMetrics, context);
            } catch (Exception e) {
                // TODO: Need better handling here if we can't reach prometheus
                context.getLogger().log("Error sending final batch: " + e.getMessage());
            }
        }

//        try {
//            MetricStreamData.Value value = new MetricStreamData.Value();
//            value.setCount(1.23);
//            MetricStreamData data = new MetricStreamData();
//            data.setMetricStreamName("test_stream_name");
//            data.setMetricName("test_metric");
//            data.setTimestamp(Instant.now().toEpochMilli());
//            data.setValue(value);
//            batchMetrics.add(data);
//            sendMetricBatch(batchMetrics, context);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

        FirehoseEventProcessingResult response = new FirehoseEventProcessingResult();
        response.setRecords(responseRecords);
        return response;
    }

    private String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variable missing: " + name);
        }
        return value;
    }

    private boolean sendMetricBatch(List<CloudWatchMetric.Metric> metrics, Context context)
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

        System.out.printf("Canonical Request: '%s'\n", canonicalRequest.replace("\n", "\\n"));

        String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, awsRegion, SERVICE);
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign =
                ALGORITHM + "\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                hashedCanonicalRequest;

        System.out.printf("String to Sign: '%s'\n", stringToSign);

        byte[] signingKey = getSignatureKey(credentials.secretAccessKey(), dateStamp, awsRegion, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // Add signing information to the request
        String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                ALGORITHM, credentials.accessKeyId(), credentialScope, signedHeaders, signature);

        System.out.printf("Sending %d bytes payload to prometheus at %s\n", compressedData.length, restApiEndpoint);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
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

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    System.out.printf("Returned status code %s: %s\n", statusCode,
                            new String(response.getEntity().getContent().readAllBytes(), Charset.defaultCharset())
                    );
                    return false;
                }
                return true;
            }
        }
    }

    private byte[] serializeToProto(List<CloudWatchMetric.Metric> metrics) {
        Remote.WriteRequest.Builder writeRequest = Remote.WriteRequest.newBuilder();
        for (CloudWatchMetric.Metric metric : metrics) {
            Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
            if (metric.dimensions != null) {
                for (Map.Entry<String,String> dimension : metric.dimensions.entrySet()) {
                    timeSeries.addLabels(Types.Label.newBuilder()
                            .setName(sanitize(dimension.getKey()))
                            .setValue(dimension.getValue())
                            .build());
                }
            }
            timeSeries.addLabels(Types.Label.newBuilder()
                    .setName("__name__")
                    .setValue(metric.metricName)
            );
            timeSeries.addSamples(Types.Sample.newBuilder()
                    // Convert seconds to milliseconds
                    .setTimestamp(metric.timestamp * 1000L)
                    .setValue(metric.value.count)
                    .build());
            writeRequest.addTimeseries(timeSeries.build());
            Types.MetricMetadata.Builder metaData = Types.MetricMetadata.newBuilder();
            if (metric.unit != null) {
                metaData.setUnit(metric.unit);
                if (metric.unit.equals("Count")) {
                    metaData.setType(Types.MetricMetadata.MetricType.COUNTER);
                }
                // TODO: What about other types?
            }
            metaData.setMetricFamilyName(metric.namespace);
            writeRequest.addMetadata(metaData.build());
        }

        // Serialize to Protobuf
        return writeRequest.build().toByteArray();
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

//    public static void main(String[] args) {
//        try {
//            KinesisToPrometheusLambda lambda = new KinesisToPrometheusLambda(true);
//            List<MetricStreamData> batchMetrics = new ArrayList<>();
//            MetricStreamData.Value value = new MetricStreamData.Value();
//            value.setCount(1.23);
//            MetricStreamData data = new MetricStreamData();
//            data.setMetricStreamName("test_stream_name");
//            data.setMetricName("test_metric");
//            data.setTimestamp(Instant.now().toEpochMilli());
//            data.setValue(value);
//            batchMetrics.add(data);
//            lambda.sendMetricBatch(batchMetrics, null);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
}


package com.adobe.aep.metrics;

import com.adobe.aep.metrics.records.CloudWatchStreamMetric;
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
    private final String metricNameLabel = "__name__";
    private final String metricAccountLabel = "account";

    private final Logger logger = LoggerFactory.getLogger(CloudWatchMetricsAmpWriter.class);

    public CloudWatchMetricsAmpWriter() {
        this.awsRegion = getEnvVar("REGION");
        this.ampWorkspaceId = getEnvVar("WORKSPACE_ID");
        this.restApiHost = String.format("aps-workspaces.%s.amazonaws.com", awsRegion);
        this.restApiPath = String.format("/workspaces/%s/api/v1/remote_write", ampWorkspaceId);
        this.restApiEndpoint = String.format("https://%s%s", restApiHost, restApiPath);
        this.credentials = (AwsSessionCredentials) DefaultCredentialsProvider.create().resolveCredentials();
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String logLevel = System.getenv("LOG_LEVEL");
        if (logLevel != null && !logLevel.isEmpty()) {
            // Set the system property for SLF4J Simple Logger
            // Valid values are: trace, debug, info, warn, error, off
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.toLowerCase());
        }
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
                    tally.add(metric.metricName());
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

        logger.debug("Canonical Request: '{}'", canonicalRequest.replace("\n", "\\n"));

        String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, awsRegion, SERVICE);
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign =
                ALGORITHM + "\n" +
                        amzDate + "\n" +
                        credentialScope + "\n" +
                        hashedCanonicalRequest;

        logger.debug("String to Sign: '{}'", stringToSign);

        byte[] signingKey = getSignatureKey(credentials.secretAccessKey(), dateStamp, awsRegion, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // Add signing information to the request
        String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                ALGORITHM, credentials.accessKeyId(), credentialScope, signedHeaders, signature);

        logger.debug("Sending {} bytes payload to prometheus at {}", compressedData.length, restApiEndpoint);

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
                logger.debug("Returned status code {}: {}", statusCode,
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
        // To make sure all the properties are aggregated correctly, first we will group by the metric name. This will
        metrics.stream()
                .collect(Collectors.groupingBy(CloudWatchStreamMetric::accountId))
                .forEach((accountId, accountGroupedMetrics) -> {
            logger.debug("Aggregating {} metrics for '{}'", accountGroupedMetrics.size(), accountId);
            accountGroupedMetrics.stream()
                    .collect(Collectors.groupingBy(CloudWatchStreamMetric::metricName))
                    .forEach((metricName, nameGroupedMetrics) -> {
                logger.debug("Aggregating {} metrics for '{}'", nameGroupedMetrics.size(), metricName);
                nameGroupedMetrics.stream()
                        .collect(Collectors.groupingBy(CloudWatchStreamMetric::dimensions))
                        .forEach((dimensions, dimensionGroupedMetrics) -> {
                    Pair<Types.TimeSeries, Types.MetricMetadata> pair = this.createWriteRequestPair(accountId, metricName, dimensions, dimensionGroupedMetrics);
                    logger.debug("Write record for {}, adding {} samples of type {}", metricName, pair.first().getSamplesCount(), pair.second().getUnit());
                    writeRequest.addTimeseries(pair.first());
                    writeRequest.addMetadata(pair.second());
                });
            });
        });


        // Serialize to Protobuf
        return writeRequest.build().toByteArray();
    }

    /**
     * This function creates a protobuf `TimeSeries` for a particular metric (given by `metricName`) and populates the
     * samples with the aggregated data from the list of metric stream items (given by `metrisValues`). It also adds
     * an account label and labels for each of the given dimensions.
     *
     * It also creates a protobuf `MetricMetadata` for the metric and populates it from values in the given metric
     * stream list. These two are returns as pairs.
     *
     * @param accountId The AWS account from which the data originates
     * @param metricName The name of the metric
     * @param dimensions Metric dimensions to be added as labels
     * @param metricsValues The list of the metrics from which values and timestamps will be extracted
     * @return A pair of timeseries and metadata for a particular metric
     */
    private Pair<Types.TimeSeries, Types.MetricMetadata> createWriteRequestPair(
            String accountId,
            String metricName,
            Map<String, String> dimensions,
            List<CloudWatchStreamMetric> metricsValues
        ) {
        Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
        Types.MetricMetadata.Builder metaData = Types.MetricMetadata.newBuilder();
        if (dimensions != null) {
            for (Map.Entry<String, String> dimension : dimensions.entrySet()) {
                timeSeries.addLabels(Types.Label.newBuilder()
                        .setName(sanitizeLabel(dimension.getKey()))
                        .setValue(dimension.getValue())
                        .build());
                logger.debug("Adding dimension {} with value {} for metric '{}'", dimension.getKey(), dimension.getValue(), metricName);
            }
        }
        timeSeries.addLabels(Types.Label.newBuilder()
                .setName(metricNameLabel)
                .setValue(this.sanitizeName(metricName))
        );
        timeSeries.addLabels(Types.Label.newBuilder()
                .setName(metricAccountLabel)
                .setValue(accountId)
        );
        for (CloudWatchStreamMetric metric : metricsValues) {
            Types.Sample.Builder sample = Types.Sample.newBuilder();
            sample.setTimestamp(metric.timestamp());
            if (metric.unit() != null) {
                metaData.setUnit(metric.unit());
                if (metric.unit().equals("Count")) {
                    metaData.setType(Types.MetricMetadata.MetricType.COUNTER);
                    sample.setValue(metric.value().count());
                }
                // TODO: What about other types?
            }
            logger.debug("Adding sample {} at timestamp {} for metric '{}'", sample.getValue(), sample.getTimestamp(), metricName);
            timeSeries.addSamples(sample.build());
            metaData.setMetricFamilyName(sanitizeLabel(metric.namespace()));
        }
        return new Pair<>(timeSeries.build(), metaData.build());
    }

    /**
     * This method takes an input string and replaces any noncon
     * @param input
     * @param regex
     * @return
     */
    private String sanitize(String input, String regex) {
        if (input.matches(regex)) {
            return input;
        } else {
            String sanitized = input.replaceAll("[^a-zA-Z0-9_]", "_");
            if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
                sanitized = "_" + sanitized.substring(1);
            }
            return sanitized;
        }
    }

    private String sanitizeName(String input) {
        return this.sanitize(input, "[a-zA-Z_:][a-zA-Z0-9_:]*");
    }

    private String sanitizeLabel(String input) {
        return this.sanitize(input, "[a-zA-Z_][a-zA-Z0-9_]*");
    }

    record Pair<F, S>(F first, S second) {
    }
}

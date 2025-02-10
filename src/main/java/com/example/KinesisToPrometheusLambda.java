package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xerial.snappy.Snappy;
import prometheus.Remote;
import prometheus.Types;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.example.AwsV4SigningUtils.*;


public class KinesisToPrometheusLambda implements RequestHandler<KinesisFirehoseEvent, RecordProcessingResult> {

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


    public KinesisToPrometheusLambda() {
        this.awsRegion = getRequiredEnvVar("REGION");
        this.ampWorkspaceId = getRequiredEnvVar("WORKSPACE_ID");
        this.restApiHost = String.format("aps-workspaces.%s.amazonaws.com", awsRegion);
        this.restApiPath = String.format("/workspaces/%s/api/v1/remote_write", ampWorkspaceId);
        this.restApiEndpoint = String.format("https://%s%s", restApiHost, restApiPath);
        this.credentials = (AwsSessionCredentials) DefaultCredentialsProvider.create().resolveCredentials();
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

//        this.restApiHost = "localhost:9090";
//        this.restApiPath = "/api/v1/write";
//        this.restApiEndpoint = String.format("http://%s%s", restApiHost, restApiPath);
    }

    @Override
    public RecordProcessingResult handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {

        List<RecordProcessingResult.Record> responseRecords = new ArrayList<>();
        List<MetricStreamData> batchMetrics = new ArrayList<>();
//        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
//            context.getLogger().log(String.format("Received record %s", record.getRecordId()));
//            try {
//                MetricStreamData metricData = parseAndValidateRecord(record);
//                context.getLogger().log(String.format("Parsed record in metric %s", metricData.getMetricName()));
//                batchMetrics.add(metricData);
//                if (batchMetrics.size() >= MAX_BATCH_SIZE) {
//                    sendMetricBatch(batchMetrics, context);
//                    batchMetrics.clear();
//                }
//                responseRecords.add(RecordProcessingResult.createSuccessResult(record.getRecordId()));
//            // TODO: Code smell here, we shan't be catching all Exceptions and swallowing them
//            } catch (Exception e) {
//                context.getLogger().log("Error processing record: " + e.getMessage());
//                responseRecords.add(RecordProcessingResult.createFailureResult(record.getRecordId()));
//            }
//        }
//
//        if (!batchMetrics.isEmpty()) {
//            try {
//                sendMetricBatch(batchMetrics, context);
//            } catch (Exception e) {
//                // TODO: Need better handling here if we can't reach prometheus
//                context.getLogger().log("Error sending final batch: " + e.getMessage());
//            }
//        }

        // --- remove everything below when working
        try {
            MetricStreamData.Value value = new MetricStreamData.Value();
            value.setCount(1.23);
            MetricStreamData data = new MetricStreamData();
            data.setMetricStreamName("test_stream_name");
            data.setMetricName("test_metric");
            data.setTimestamp(Instant.now().toEpochMilli());
            data.setValue(value);
            batchMetrics.add(data);
            sendMetricBatch(batchMetrics, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // --- remove everything above when working

        RecordProcessingResult response = new RecordProcessingResult();
        response.setRecords(responseRecords);
        return response;
    }

    private MetricStreamData parseAndValidateRecord(KinesisFirehoseEvent.Record record)
            throws IllegalArgumentException, JsonProcessingException {
        String data = new String(record.getData().array(), StandardCharsets.UTF_8);
        MetricStreamData metricData = objectMapper.readValue(data, MetricStreamData.class);
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
        return metricData;
    }

    private String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variable missing: " + name);
        }
        return value;
    }

    private void sendMetricBatch(List<MetricStreamData> metrics, Context context) throws Exception {
        try {
            byte[] prometheusData = serializeToProto(metrics);
            byte[] compressedData = Snappy.compress(prometheusData);

            String amzDate = dateFormat.format(new Date());
            String dateStamp = amzDate.substring(0,8);

            // Create the canonical request
            String canonicalUri = restApiPath;
            String canonicalQuerystring = "";
            String sha256HashOfEmptyString = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

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
//                    "content-encoding: " + CONTENT_ENCODING + "\n" +
                    "content-type:" + CONTENT_TYPE + "\n" +
                    "host:" + restApiHost + "\n" +
                    "x-amz-content-sha256:" + payloadHash + "\n" +
                    "x-amz-date:" + amzDate + "\n" +
                    "x-amz-security-token:" + credentials.sessionToken() + "\n" +
//                    "x-prometheus-remote-write-version:0.1.0" + "\n";
                    "x-api-key:" + API_KEY + "\n";  // terminate last with \n
            String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date;x-api-key";
//            String signedHeaders = "content-encoding;content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token;x-prometheus-remote-write-version";

// The request signature we calculated does not match the signature you provided. Check your AWS Secret Access Key and signing method. Consult the service documentation for details.
//
// The Canonical String for this request should have been
// 'POST
// /workspaces/ws-102876bb-266b-4c7d-9270-5d2824b4885c/api/v1/remote_write
//
// content-type:application/x-protobuf
// host:aps-workspaces.us-west-2.amazonaws.com
// x-amz-content-sha256:d745fece628736044a89836a46c03b2b2d6a4a5b445440bcd4db681079eab55b
// x-amz-date:20250210T214841Z
// x-api-key:
//
// content-type;host;x-amz-content-sha256;x-amz-date;x-api-key
// e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
//
// The String-to-Sign should have been
// 'AWS4-HMAC-SHA256
// 20250210T214841Z
// 20250210/us-west-2/aps/aws4_request
// 8c458a52adaf08ddb70314ce18fba421a5c962e96924e3f0a6f708b678e0ae1c'
            
            String canonicalRequest =
                    METHOD + "\n" +
                    canonicalUri + "\n" +
                    canonicalQuerystring + "\n" +
                    canonicalHeaders + "\n" +
                    signedHeaders + "\n" +
                    sha256HashOfEmptyString;

            System.out.printf("Canonical Request: '%s'\n", canonicalRequest.replace("\n", "\\n"));

// Canonical Request:
// 'POST
// /workspaces/ws-102876bb-266b-4c7d-9270-5d2824b4885c/api/v1/remote_write
//
// content-type:application/x-protobuf
// host:aps-workspaces.us-west-2.amazonaws.com
// x-amz-content-sha256:25ca89bc2374dd664ccc11b6478d377e4208e5bb3f90f9adbaabb0058e6669ca
// x-amz-date:20250209T232749Z
// x-api-key:
//
// content-type;host;x-amz-content-sha256;x-amz-date;x-api-key
// 25ca89bc2374dd664ccc11b6478d377e4208e5bb3f90f9adbaabb0058e6669ca'

// The Canonical String for this request should have been
// 'POST
// /workspaces/ws-102876bb-266b-4c7d-9270-5d2824b4885c/api/v1/remote_write
//
// content-type:application/x-protobuf
// host:aps-workspaces.us-west-2.amazonaws.com
// x-amz-content-sha256:25ca89bc2374dd664ccc11b6478d377e4208e5bb3f90f9adbaabb0058e6669ca
// x-amz-date:20250210T181946Z
// x-api-key:
//
// content-type;host;x-amz-content-sha256;x-amz-date;x-api-key
// e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'

// String to Sign:
// 'AWS4-HMAC-SHA256
// 20250209T232749Z
// 20250209/us-west-2/aps/aws4_request
// cdbde895095569533e4cbaa23abd70eb007803b10314c85ac5ea196daf316dc0'

// The String-to-Sign should have been
// 'AWS4-HMAC-SHA256
// 20250209T232749Z
// 20250209/us-west-2/aps/aws4_request
// 88b6c6ae461d1d33b2a1d95ba77a46555e33ff6214b075cd1f89f0b72002ac88'

// hash of empty string = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

            String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, awsRegion, SERVICE);
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign =
                    ALGORITHM + "\n" +
                    amzDate + "\n" +
                    credentialScope + "\n" +
                    hashedCanonicalRequest;

            System.out.printf("String to Sign: '%s'\n", stringToSign.replace("\n", "\\n"));

            byte[] signingKey = getSignatureKey(credentials.secretAccessKey(), dateStamp, awsRegion, SERVICE);
            String signature = hmacSha256Hex(signingKey, stringToSign);

            // Add signing information to the request
            String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                    ALGORITHM, credentials.accessKeyId(), credentialScope, signedHeaders, signature);

//            System.out.printf("Sending %d bytes payload to prometheus at %s\n", compressedData.length, restApiEndpoint);

//    *** Request Details ***
//    Method: POST
//    URI: https://aps-workspaces.us-west-2.amazonaws.com/workspaces/ws-209610ee-1c90-4030-818a-86ad165dbe50/api/v1/remote_write
//    Headers:
//    - Authorization: AWS4-HMAC-SHA256 Credential=ASIATP4E4S6WGAMNHKAI/20250209/us-west-2/aps/aws4_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date;x-api-key, Signature=de96ee92a5560b33a20f2427444e5378e5c0d4c38f7da8acae521bb8bd5f1ee0
//            - content-encoding: snappy
//            - content-type: application/x-protobuf
//            - host: aps-workspaces.us-west-2.amazonaws.com
//            - x-amz-content-sha256: 7479e5c85cae519e8e05718ae7590df6f23c8a98505c7d09f541a4205c1bd959
//            - x-amz-date: 20250209T020908Z
//            - x-amz-security-token: IQoJb3JpZ2luX2VjEIL//////////wEaCXVzLXdlc3QtMiJGMEQCIQDEoxihvmsbEUWIy5QvaoZ3bnZ4L48lpsrzasiGYA/mwQIfOttH71zn9NS7aM0RAE7J7s779oy5S8+V4kfD/QM1USr5Agib//////////8BEAAaDDI0MDI1OTk5NTU2NCIMwgcJ9DlgGR7CdBX3Ks0Cq07BTKJtM1MeZHz8tS48DuysGs9LnKw652vtz6ItF+/HPfWT3lYzMOiRMVVSVy26WZb3YWT/umstT2+l90QujXSdRiHp4JE8HeZoRPiCjofXntElML3k5Wo1tN7vL33//usGc7tJaMSur5tbmt2idV7n83Yj8jIA4oWBRjSFUBhdjKvaPMPxeWVZB6RV4v8Pr9dSlkmyTFDqIhfakrNK3d0NgAAfAzNZDWVKR5hrnj7GrboIszXRThM8tlEs9yyPHpgeZaUSmjT23LWd9/Km+4PzttYRhxD2dn0nyTKH2oRLMVnSxdrm3EgumCiACzxnQZn609GYXp6kPtYUXWDYp0MYnI6ru8cs1xsoP3dQXmjPdN1P3IV3dDBUCtiFWJw0tVqXjsVrkO3WByU99KXm0bEBns5slxQ12BI03fpfmYZTj//Fi9Tvv8TMOjXtMMGcoL0GOp8BVsbQwzV40kEI3CaqF/4RxMl5BPVh6mfiIXGSQ4ZMISPcEOwlbjfbR4muOrjH8l3yT0v7WWi5sWBf8qUrz4SsAUT1/3BzFp3QLIAwFFFjK0pr1xrL6QuUX7G76hBipt/ilihMkr/hXgvmduOG4Hw3arGfEqhYztd/v7/iLwqkAnpiPCk6AAhgQfCJVgos06LDlMzWHaUKCLQr4rj/JQ15
//            - x-prometheus-remote-write-version: 0.1.0


            SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.POST)
                    .uri(URI.create(restApiEndpoint))
                    .putHeader("host", restApiHost)
                    .putHeader("x-amz-date", amzDate)
                    .putHeader("x-amz-security-token", credentials.sessionToken())
                    .putHeader("x-amz-content-sha256", payloadHash)
                    .putHeader("content-encoding", CONTENT_ENCODING)
                    .putHeader("content-type", CONTENT_TYPE)
                    .putHeader("x-prometheus-remote-write-version", "0.1.0")
                    .putHeader("Authorization", authorizationHeader)
                    .contentStreamProvider(() -> new ByteArrayInputStream(compressedData))
                    .build();

            printRequest(sdkRequest);

            SdkHttpClient httpClient = ApacheHttpClient.builder()
                    .socketTimeout(Duration.ofSeconds(30))
                    .connectionTimeout(Duration.ofSeconds(30))
                    .build();

            HttpExecuteResponse response = httpClient.prepareRequest(
                    HttpExecuteRequest.builder()
                            .request(sdkRequest)
                            .build()
                    )
                    .call();

            int statusCode = response.httpResponse().statusCode();
            response.responseBody().map(stream -> {
                try {
                    String body = new String(stream.readAllBytes(), Charset.defaultCharset());
                    System.out.println(body);
                    return body;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

//            if (statusCode != 200) {
//                String responseBody = new String(response.responseBody()
//                        .orElseThrow(() -> new RuntimeException("No response body"))
//                        .readAllBytes());
//                throw new RuntimeException("Request failed with status: " + statusCode + ", body: " + responseBody);
//            }

            System.out.printf("%d metrics sent to Prometheus: %s\n", metrics.size(), statusCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] serializeToProto(List<MetricStreamData> metrics) {
        Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
        for (MetricStreamData metric : metrics) {
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

        Types.MetricMetadata.Builder metadata = Types.MetricMetadata.newBuilder()
                .setTypeValue(Types.MetricMetadata.MetricType.COUNTER_VALUE);
        // Create a WriteRequest
        Remote.WriteRequest writeRequest = Remote.WriteRequest.newBuilder()
                .addTimeseries(timeSeries.build())
                .addMetadata(metadata.build())
                .build();

        // Serialize to Protobuf
        return writeRequest.toByteArray();
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private void printRequest(SdkHttpFullRequest signedRequest) throws IOException {
        System.out.println("*** Request Details ***");
        System.out.println("  Method: " + signedRequest.method());
        System.out.println("  URI: " + signedRequest.getUri());
        System.out.println("  Headers: ");
        signedRequest.headers().forEach((key, value) ->
                System.out.printf("  - %s: %s\n", key, String.join(", ", value))
        );
    }

    public static void main(String[] args) {
        KinesisToPrometheusLambda lambda = new KinesisToPrometheusLambda();
        List<MetricStreamData> batchMetrics = new ArrayList<>();

        try {
            MetricStreamData.Value value = new MetricStreamData.Value();
            value.setCount(1.23);
            MetricStreamData data = new MetricStreamData();
            data.setMetricStreamName("test_stream_name");
            data.setMetricName("test_metric");
            data.setTimestamp(Instant.now().toEpochMilli());
            data.setValue(value);
            batchMetrics.add(data);
            lambda.sendMetricBatch(batchMetrics, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}


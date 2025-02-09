package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xerial.snappy.Snappy;
import prometheus.Remote;
import prometheus.Types.Label;
import prometheus.Types.Sample;
import prometheus.Types.TimeSeries;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.example.AwsV4SigningUtils.*;


public class KinesisToPrometheusLambda
        implements RequestHandler<KinesisFirehoseEvent, KinesisToPrometheusLambda.KinesisFirehoseResponse> {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_METRIC_NAME_LENGTH = 200;
    private static final int MAX_DIMENSION_VALUE_LENGTH = 100;

//    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
//    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
//    private static final String X_API_KEY = System.getenv("X_API_KEY");
//    private static final String RESTAPIHOST = System.getenv("RESTAPIHOST");
//    private static final String RESTAPIPATH = System.getenv("RESTAPIPATH");

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
    private final Aws4Signer signer = Aws4Signer.create();
    private final Aws4SignerParams signerParams;
    private final AwsSessionCredentials credentials;

    // Create a datetime object for signing
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);


    public KinesisToPrometheusLambda() {
        this.awsRegion = getRequiredEnvVar("REGION");
        this.ampWorkspaceId = getRequiredEnvVar("WORKSPACE_ID");
        this.restApiHost = String.format("aps-workspaces.%s.amazonaws.com", awsRegion);
        this.restApiPath = String.format("/workspaces/%s/api/v1/remote_write", ampWorkspaceId);
        this.restApiEndpoint = String.format("https://%s%s", restApiHost, restApiPath);

        System.out.println(restApiEndpoint);
        credentials = (AwsSessionCredentials) DefaultCredentialsProvider.create().resolveCredentials();
        System.out.println(credentials.accessKeyId());
        System.out.println(credentials.secretAccessKey());
        System.out.println(credentials.sessionToken());
        signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingName("aps") // "aps" is the service name for AMP
                .signingRegion(Region.of(awsRegion))
                .build();

        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    }

//    private static final String STREAM_NAME = "your-kinesis-stream"; // Replace with your stream name
//    private static final String WORKSPACE_ID = "ws-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"; // Your AMP workspace ID
//    private static final String PROMETHEUS_URL =
//
//    private static final KinesisClient kinesisClient = KinesisClient.builder()
//            .region(Region.of(REGION))
//            .credentialsProvider(DefaultCredentialsProvider.create())
//            .httpClient(ApacheHttpClient.create())
//            .build();
//
//    public void handleRequest() {
//        String shardIterator = getShardIterator();
//        if (shardIterator != null) {
//            List<Record> records = getRecords(shardIterator);
//            for (Record record : records) {
//                processRecord(record);
//            }
//        }
//    }

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

    private String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variable missing: " + name);
        }
        return value;
    }

    @Override
    public KinesisFirehoseResponse handleRequest(KinesisFirehoseEvent firehoseEvent, Context context) {

        List<KinesisFirehoseResponse.Record> responseRecords = new ArrayList<>();

//        List<MetricStreamData> batchMetrics = new ArrayList<>();
//        for (KinesisFirehoseEvent.Record record : firehoseEvent.getRecords()) {
//            context.getLogger().log(String.format("Received record %s", record.getRecordId()));
//            try {
//                MetricStreamData metricData = parseAndValidateRecord(record);
//                context.getLogger().log(String.format("Parsed record in metric %s", metricData.metricName));
//                batchMetrics.add(metricData);
//                if (batchMetrics.size() >= MAX_BATCH_SIZE) {
//                    sendMetricBatch(batchMetrics, context);
//                    batchMetrics.clear();
//                }
//                responseRecords.add(createSuccessResponse(record.getRecordId()));
//            // TODO: Code smell here, we shan't be catching all Exceptions and swallowing them
//            } catch (Exception e) {
//                context.getLogger().log("Error processing record: " + e.getMessage());
//                responseRecords.add(createFailureResponse(record.getRecordId()));
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
        sendToPrometheus(1.23);
        KinesisFirehoseResponse response = new KinesisFirehoseResponse();
        response.setRecords(responseRecords);
        return response;
    }

//    private MetricStreamData parseAndValidateRecord(KinesisFirehoseEvent.Record record) throws Exception {
//        String data = new String(record.getData().array(), StandardCharsets.UTF_8);
//        MetricStreamData metricData = objectMapper.readValue(data, MetricStreamData.class);
//        if (metricData.getMetricName() == null || metricData.getMetricName().length() > MAX_METRIC_NAME_LENGTH) {
//            throw new IllegalArgumentException("Invalid metric name");
//        }
//
//        if (metricData.getDimensions() != null) {
//            for (Map.Entry<String, String> dim : metricData.getDimensions().entrySet()) {
//                if (dim.getValue().length() > MAX_DIMENSION_VALUE_LENGTH) {
//                    throw new IllegalArgumentException("Dimension value too long: " + dim.getKey());
//                }
//            }
//        }
//        return metricData;
//    }
//
//    private KinesisFirehoseResponse.Record createSuccessResponse(String recordId) {
//        KinesisFirehoseResponse.Record response = new KinesisFirehoseResponse.Record();
//        response.setRecordId(recordId);
//        response.setResult(KinesisFirehoseResponse.Result.Ok);
//        return response;
//    }
//
//    private KinesisFirehoseResponse.Record createFailureResponse(String recordId) {
//        KinesisFirehoseResponse.Record response = new KinesisFirehoseResponse.Record();
//        response.setRecordId(recordId);
//        response.setResult(KinesisFirehoseResponse.Result.ProcessingFailed);
//        return response;
//    }


    private void sendToPrometheus(double value) {
        try {
            // Construct Protobuf payload
            Remote.WriteRequest.Builder writeRequest = Remote.WriteRequest.newBuilder();
            TimeSeries.Builder timeSeries = TimeSeries.newBuilder();
            timeSeries.addLabels(Label.newBuilder()
                    .setName("test_label_name")
                    .setValue("test_label_value")
            );

            timeSeries.addSamples(Sample.newBuilder()
                    .setValue(value)
                    .setTimestamp(Instant.now().toEpochMilli())
            );

            writeRequest.addTimeseries(timeSeries.build());

            // Convert to Protobuf binary
            byte[] protobufData = writeRequest.build().toByteArray();
            byte[] compressedData = Snappy.compress(protobufData);


            String amzDate = dateFormat.format(new Date());
            String dateStamp = amzDate.substring(0,8);

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

//  POST
//  /workspaces/ws-209610ee-1c90-4030-818a-86ad165dbe50/api/v1/remote_write
//
//  content-type:application/x-protobuf
//  host:aps-workspaces.us-west-2.amazonaws.com
//  x-amz-content-sha256:a5b535cbc93028ed2bf15e3cdfd8e3d8d55b5e07eaeb6d9cfe7fb9374fda3c5f
//  x-amz-date:20250209T040109Z
//  x-api-key:
//
//  content-type;host;x-amz-content-sha256;x-amz-date;x-api-key
//  a5b535cbc93028ed2bf15e3cdfd8e3d8d55b5e07eaeb6d9cfe7fb9374fda3c5f

// POST
// /workspaces/ws-209610ee-1c90-4030-818a-86ad165dbe50/api/v1/remote_write
//
// content-type:application/x-protobuf
// host:aps-workspaces.us-west-2.amazonaws.com
// x-amz-content-sha256:a5b535cbc93028ed2bf15e3cdfd8e3d8d55b5e07eaeb6d9cfe7fb9374fda3c5f
// x-amz-date:20250209T040109Z
// x-api-key:
//
// content-type;host;x-amz-content-sha256;x-amz-date;x-api-key
// e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

            String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, awsRegion, SERVICE);

            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign =
                    ALGORITHM + "\n" +
                    amzDate + "\n" +
                    credentialScope + "\n" +
                    hashedCanonicalRequest;

            System.out.printf("String to Sign: '%s'\n", stringToSign);

// String to Sign:
// 'AWS4-HMAC-SHA256
// 20250209T040109Z
// 20250209/us-west-2/aps/aws4_request
// 3078753c3d67f6257b553408f34a3aa5116552b5ee384d470feb2ed8eb07701e

// The String-to-Sign should have been
// 'AWS4-HMAC-SHA256
// 20250209T040109Z
// 20250209/us-west-2/aps/aws4_request
// 0abe036cf735cf8268dbad50f5cef4fda46d9fbfa8eba8f6a8d73b214889ecec

            byte[] signingKey = getSignatureKey(credentials.secretAccessKey(), dateStamp, awsRegion, SERVICE);
            String signature = hmacSha256Hex(signingKey, stringToSign);

            // Add signing information to the request
            String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                    ALGORITHM, credentials.accessKeyId(), credentialScope, signedHeaders, signature);

            System.out.printf("Sending data to prometheus at %s\n", restApiEndpoint);

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
                    .contentStreamProvider(() ->
                            AbortableInputStream.create(new ByteArrayInputStream(compressedData)))
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

            response.responseBody().map(stream -> {
                try {
                    String body = new String(stream.readAllBytes(), Charset.defaultCharset());
                    System.out.println(body);
                    return body;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            System.out.printf("Metric %f sent to Prometheus: %s\n", value, response.httpResponse().statusCode());

        } catch (Exception e) {
            e.printStackTrace();
        }
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

//    public static byte[] formatMetricBatch(List<LambdaHandler.MetricStreamData> metrics) {
//
//        Types.TimeSeries.Builder timeSeries = Types.TimeSeries.newBuilder();
//        for (LambdaHandler.MetricStreamData metric : metrics) {
//            if (metric.getDimensions() != null) {
//                for (Map.Entry<String, String> dimension : metric.getDimensions().entrySet()) {
//                    timeSeries.addLabels(Types.Label.newBuilder()
//                        .setName(sanitize(dimension.getKey()))
//                        .setValue(dimension.getValue().replace("\"", "\\\""))
//                        .build());
//                }
//            }
//            timeSeries.addLabels(Types.Label.newBuilder()
//                .setName("metric_stream")
//                .setValue(metric.getMetricStreamName())
//            );
//            timeSeries.addSamples(Types.Sample.newBuilder()
//                    .setTimestamp(metric.getTimestamp())
//                    .setValue(metric.getValue().getCount())
//                    .build());
//        }
//
////        Types.MetricMetadata.Builder metricMetadata = Types.MetricMetadata.newBuilder()
////                .setUnit()
//        // Create a WriteRequest
//        Remote.WriteRequest writeRequest = Remote.WriteRequest.newBuilder()
//                .addTimeseries(timeSeries.build())
//                .build();
//
//        // Serialize to Protobuf
//        byte[] protobufData = writeRequest.toByteArray();
//
//
//
//        return protobufData;
//    }
//
//
//    private static String sanitize(String input) {
//        return input.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//    }

    private void sendMetricBatch(List<MetricStreamData> metrics, Context context) throws Exception {
//        byte[] prometheusData = PrometheusFormatter.formatMetricBatch(metrics);
//        String prometheusUrl = getRequiredEnvVar("PROMETHEUS_REMOTE_WRITE_URL");
//        String roleArn = getRequiredEnvVar("AWS_AMP_ROLE_ARN");
//        AwsCredentialsProvider credsProvider = sigV4Handler.getCredentialsProvider(roleArn);
//        AwsCredentials creds = credsProvider.resolveCredentials();
//        Sigv4Signer signer = new Sigv4Signer(
//                creds.accessKeyId(),
//                creds.secretAccessKey(),
//                "x-api-key",
//                URI.create(prometheusUrl));
//        String body = signer.sendRequest(new String(prometheusData));
//        HttpExecuteResponse response = sigV4Handler.send(URI.create(prometheusUrl), prometheusData);
//        handleResponse(response);
//    }
//
//    private void handleResponse(HttpExecuteResponse response) throws Exception {
//        int statusCode = response.httpResponse().statusCode();
//        if (statusCode != 200) {
//            String responseBody = new String(response.responseBody()
//                    .orElseThrow(() -> new RuntimeException("No response body"))
//                    .readAllBytes());
//            throw new RuntimeException("Request failed with status: " + statusCode + ", body: " + responseBody);
//        }
    }
}


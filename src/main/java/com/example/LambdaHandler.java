//package com.example;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.xerial.snappy.Snappy;
//import prometheus.Remote;
//import prometheus.Types;
//import software.amazon.awssdk.auth.credentials.AwsCredentials;
//import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
//import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
//import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
//import software.amazon.awssdk.auth.signer.Aws4Signer;
//import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
//import software.amazon.awssdk.http.*;
//import software.amazon.awssdk.http.apache.ApacheHttpClient;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.sts.StsClient;
//import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
//import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
//
//import java.io.ByteArrayInputStream;
//import java.net.URI;
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.time.Duration;
//import java.time.Instant;
//import java.time.ZoneOffset;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.Base64;
//import java.util.List;
//import java.util.Map;
//
//public class LambdaHandler implements RequestHandler<KinesisFirehoseEvent, LambdaHandler.KinesisFirehoseResponse> {
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//    private static final int MAX_BATCH_SIZE = 500;
//    private static final int MAX_METRIC_NAME_LENGTH = 200;
//    private static final int MAX_DIMENSION_VALUE_LENGTH = 100;
//    private SigV4RequestHandler sigV4Handler;
//
//
//    private void initSigV4Handler() {
//        String region = getRequiredEnvVar("AWS_REGION");
//        String roleArn = getRequiredEnvVar("AWS_AMP_ROLE_ARN");
//        sigV4Handler = new SigV4RequestHandler(region, roleArn);
//    }
//
//    private void validateMetricData(MetricStreamData metricData) {
//    }
//
//}
//
//class PrometheusFormatter {
//}
//
//

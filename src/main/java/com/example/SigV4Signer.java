package com.example;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.amazonaws.services.lambda.runtime.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/***
 * Sample code for POSTING against an AWS API endpoint
 * See
 * https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html
 * */
public class SigV4Signer {
    private String AWS_ACCESS_KEY_ID;
    private String AWS_SECRET_ACCESS_KEY;
    private String AWS_SESSION_TOKEN;
    private String RESTAPIHOST;
    private String RESTAPIPATH;

    private static final String METHOD = "POST";
    private static final String SERVICE = "aps"; // Changed from "execute-api"
    private static final String REGION = "us-east-1";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    // Updated constructor to include sessionToken
    public SigV4Signer(String awsAccessKeyId, String awsSecretAccessKey, String sessionToken, URI endpoint) {
        this.AWS_ACCESS_KEY_ID = awsAccessKeyId;
        this.AWS_SECRET_ACCESS_KEY = awsSecretAccessKey;
        this.AWS_SESSION_TOKEN = sessionToken;
        this.RESTAPIHOST = endpoint.getHost();
        this.RESTAPIPATH = endpoint.getPath();
    }

    public String sendRequest(String jsonPayload) throws IOException, NoSuchAlgorithmException, Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String amzDate = dateFormat.format(new Date());
        String dateStamp = amzDate.substring(0, 8);

        String canonicalUri = RESTAPIPATH;
        String canonicalQuerystring = "";
        String payloadHash = sha256Hex(jsonPayload);

        // Updated canonical headers
        String canonicalHeaders = "content-encoding:snappy\n" +
                "content-type:application/x-protobuf\n" +
                "host:" + RESTAPIHOST + "\n" +
                "x-amz-date:" + amzDate + "\n" +
                "x-amz-security-token:" + AWS_SESSION_TOKEN + "\n" +
                "x-prometheus-remote-write-version:0.1.0\n";

        // Updated signed headers
        String signedHeaders = "content-encoding;content-type;host;x-amz-date;" +
                "x-amz-security-token;x-prometheus-remote-write-version";

        String canonicalRequest =
                METHOD + "\n" +
                canonicalUri + "\n" +
                canonicalQuerystring + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;

        String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, REGION, SERVICE);
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        byte[] signingKey = getSignatureKey(AWS_SECRET_ACCESS_KEY, dateStamp, REGION, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String authorizationHeader = String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                ALGORITHM, AWS_ACCESS_KEY_ID, credentialScope, signedHeaders, signature);

        String path = "https://" + RESTAPIHOST + canonicalUri;
        URL url = new URL(path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod(METHOD);
        con.setRequestProperty("content-encoding", "snappy");
        con.setRequestProperty("content-type", "application/x-protobuf");
        con.setRequestProperty("host", RESTAPIHOST);
        con.setRequestProperty("x-amz-date", amzDate);
        con.setRequestProperty("x-amz-security-token", AWS_SESSION_TOKEN);
        con.setRequestProperty("x-prometheus-remote-write-version", "0.1.0");
        con.setRequestProperty("Authorization", authorizationHeader);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK)
            System.out.println("Error: " + responseCode + " " + con.getResponseMessage());

        String responseBody = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return responseBody;
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws NoSuchAlgorithmException {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private static String hmacSha256Hex(byte[] key, String data) throws NoSuchAlgorithmException {
        return bytesToHex(hmacSha256(key, data));
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error computing HmacSHA256", e);
        }
    }

    private static String sha256Hex(String data) throws NoSuchAlgorithmException {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
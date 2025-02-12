package com.adobe.aep.metrics;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class AwsV4SigningUtils {

    public static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws NoSuchAlgorithmException {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    public static String hmacSha256Hex(byte[] key, String data) throws NoSuchAlgorithmException {
        return bytesToHex(hmacSha256(key, data));
    }

    public static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error: HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Error: Invalid key for HmacSHA256", e);
        }
    }

    public static String sha256Hex(String data) throws NoSuchAlgorithmException {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
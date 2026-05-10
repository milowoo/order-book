package com.orderbook.connector.stream.global;

import lombok.experimental.UtilityClass;
import org.knowm.xchange.service.BaseParamsDigest;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@UtilityClass
public class GlobalStreamingAuthHelper {

    // Generates signature based on timestamp
    public String sign(Instant timestamp, String secretString) {
        final SecretKey secretKey =
                new SecretKeySpec(
                        secretString.getBytes(StandardCharsets.UTF_8), BaseParamsDigest.HMAC_SHA_256);
        Mac mac = createMac(secretKey, secretKey.getAlgorithm());

        String payloadToSign = String.format("%sGET/user/verify", timestamp.getEpochSecond());
        mac.update(payloadToSign.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(mac.doFinal());
    }

    private Mac createMac(SecretKey secretKey, String hmacString) {
        try {
            Mac mac = Mac.getInstance(hmacString);
            mac.init(secretKey);
            return mac;
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid key for hmac initialization.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Illegal algorithm for post body digest. Check the implementation.");
        }
    }

    public static void main(String[] args) {
        Instant now = Instant.now();
        System.out.println(now.getEpochSecond());
        System.out.println(sign(Instant.now(), "a2d3a72f95f084890762776cf464ea0ed8a9be92502983c53da9da773a4cc9bc"));
    }
}
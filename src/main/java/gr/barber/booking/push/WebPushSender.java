package gr.barber.booking.push;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * Sends one notification to one browser, the standard way (Web Push): the message is encrypted for
 * that browser (RFC 8291, aes128gcm) and the request is signed with our keys (VAPID, RFC 8292), so
 * the push service (Google, Apple, Mozilla) can only pass it on. Plain JDK, no extra libraries.
 */
@Component
public class WebPushSender {

    private static final ECParameterSpec P256 = p256();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;

    private final PushProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebPushSender(PushProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the push service's HTTP status: 201 sent, 404/410 the browser has unsubscribed */
    public int send(String endpoint, String p256dh, String auth, String json) throws IOException, InterruptedException {
        byte[] body;
        String jwt;
        URI uri = URI.create(endpoint);
        try {
            body = encrypt(decode(p256dh), decode(auth), json.getBytes(StandardCharsets.UTF_8), newKeyPair(), randomBytes(16));
            jwt = vapidToken(uri.getScheme() + "://" + uri.getAuthority());
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not encrypt the notification", e);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(Duration.ofDays(1).toSeconds()))
                .header("Urgency", "high")
                .header("Authorization", "vapid t=" + jwt + ", k=" + properties.publicKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * The encrypted body: salt, record size, our one-time public key, then the message (RFC 8291 §3.4,
     * RFC 8188). The key pair and salt are parameters so tests can check the RFC's own example.
     */
    static byte[] encrypt(byte[] uaPublic, byte[] authSecret, byte[] plaintext, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        byte[] asPublic = raw((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // A single record, so the padding delimiter is 0x02 ("last record").
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        return concat(salt, ByteBuffer.allocate(4).putInt(RECORD_SIZE).array(), new byte[]{(byte) asPublic.length}, asPublic, ciphertext);
    }

    // A short-lived signed token saying who we are, for this push service only (RFC 8292).
    String vapidToken(String audience) throws GeneralSecurityException {
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long expires = clock.instant().plus(Duration.ofHours(12)).getEpochSecond();
        String claims = encode("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}".formatted(audience, expires, properties.subject())
                .getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey(decode(properties.privateKey())));
        signature.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + encode(signature.sign());
    }

    /** True when the private key signs what the public key verifies: the two keys belong together. */
    boolean keysMatch() {
        try {
            String token = vapidToken("https://example.com");
            int dot = token.lastIndexOf('.');
            Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
            verify.initVerify(publicKey(decode(properties.publicKey())));
            verify.update(token.substring(0, dot).getBytes(StandardCharsets.US_ASCII));
            return verify.verify(decode(token.substring(dot + 1)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    // ---- key and encoding helpers ----

    static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 public key");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static ECPrivateKey privateKey(byte[] value) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, value), P256));
    }

    static byte[] raw(ECPublicKey key) {
        return concat(new byte[]{4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url.trim());
    }

    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }
}

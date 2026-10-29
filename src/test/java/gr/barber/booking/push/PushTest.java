package gr.barber.booking.push;

import com.sun.net.httpserver.HttpServer;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phone notifications end to end, with a stand-in for the push service (Google, Apple) that records
 * what it receives. "Now" is Monday 7 January 2030, 10:00 Athens; the barber works Tuesdays.
 */
class PushTest extends IntegrationTest {

    private static final String SERVER_KEY = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

    @Autowired
    private PushSubscriptionRepository subscriptions;

    private record Received(Map<String, List<String>> headers, byte[] body) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger answer = new AtomicInteger(201);
    private HttpServer pushService;

    // The phone's own keys, as its browser would make them.
    private KeyPair phoneKeys;
    private byte[] authSecret;

    private User barberUser;
    private User customer;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext("/send", exchange -> {
            received.add(new Received(Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer.get(), -1);
            exchange.close();
        });
        pushService.start();

        phoneKeys = WebPushSender.newKeyPair();
        authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);

        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        haircut = createService(barber, "Haircut", 30, "12.00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "18:00");
    }

    @AfterEach
    void stop() {
        pushService.stop(0);
    }

    @Test
    void theKeyForSubscribingIsPublic() throws Exception {
        mockMvc.perform(get("/push/key")).andExpect(status().isOk()).andExpect(jsonPath("$.publicKey").value(SERVER_KEY));
    }

    @Test
    void theBarbersPhoneHearsAboutANewBookingRightAway() throws Exception {
        subscribe(barberUser).andExpect(status().isNoContent());

        mockMvc.perform(post("/appointments").header("Authorization", bearer(customer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":%d,\"serviceId\":%d,\"startTime\":\"2030-01-08T10:00\"}".formatted(barber.getId(), haircut.getId())))
                .andExpect(status().isCreated());

        assertThat(received).hasSize(1);
        Received push = received.getFirst();
        assertThat(push.headers().get("Content-encoding")).containsExactly("aes128gcm");
        assertThat(push.headers().get("Ttl")).containsExactly("86400");
        assertThat(push.headers().get("Authorization").getFirst()).startsWith("vapid t=").endsWith(", k=" + SERVER_KEY);
        // Only the phone can read it.
        assertThat(decrypt(push.body())).isEqualTo(
                "{\"title\":\"Νέο ραντεβού\",\"body\":\"Τρίτη 8 Ιανουαρίου, 10:00 · Haircut · Maria\",\"url\":\"/barber\"}");
    }

    @Test
    void aTestNotificationShowsTheyWorkAndGoneBrowsersAreForgotten() throws Exception {
        subscribe(barberUser);
        mockMvc.perform(post("/push/test").header("Authorization", bearer(barberUser))).andExpect(jsonPath("$.delivered").value(1));
        assertThat(decrypt(received.getFirst().body())).contains("Οι ειδοποιήσεις δουλεύουν");

        answer.set(410); // the app was removed from the phone
        mockMvc.perform(post("/push/test").header("Authorization", bearer(barberUser))).andExpect(jsonPath("$.delivered").value(0));
        assertThat(subscriptions.count()).isZero();
    }

    @Test
    void turningThemOffStopsThem() throws Exception {
        subscribe(barberUser);
        mockMvc.perform(post("/push/unsubscribe").header("Authorization", bearer(barberUser)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"%s\"}".formatted(endpoint())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/push/test").header("Authorization", bearer(barberUser))).andExpect(jsonPath("$.delivered").value(0));
        assertThat(received).isEmpty();
        mockMvc.perform(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private String endpoint() {
        return "http://localhost:" + pushService.getAddress().getPort() + "/send/phone-1";
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(User who) throws Exception {
        return mockMvc.perform(post("/push/subscriptions").header("Authorization", bearer(who)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint(),
                        WebPushSender.encode(WebPushSender.raw((ECPublicKey) phoneKeys.getPublic())), WebPushSender.encode(authSecret))));
    }

    // What the phone's browser does with the message (RFC 8291), written out separately from the sender.
    private String decrypt(byte[] body) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int keyLength = body[20];
        byte[] serverPublic = Arrays.copyOfRange(body, 21, 21 + keyLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + keyLength, body.length);
        assertThat(ByteBuffer.wrap(body, 16, 4).getInt()).isEqualTo(4096);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(phoneKeys.getPrivate());
        agreement.doPhase(WebPushSender.publicKey(serverPublic), true);
        byte[] phonePublic = WebPushSender.raw((ECPublicKey) phoneKeys.getPublic());

        byte[] ikm = hmac(hmac(authSecret, agreement.generateSecret()),
                join("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), phonePublic, serverPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, join("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, join("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}

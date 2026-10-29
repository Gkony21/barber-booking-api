package gr.barber.booking.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushSenderTest {

    // The worked example in RFC 8291, Appendix A.
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void encryptsExactlyAsTheStandardsExample() throws Exception {
        KeyPair serverKeys = new KeyPair(WebPushSender.publicKey(WebPushSender.decode(AS_PUBLIC)),
                WebPushSender.privateKey(WebPushSender.decode(AS_PRIVATE)));

        byte[] body = WebPushSender.encrypt(WebPushSender.decode(UA_PUBLIC), WebPushSender.decode(AUTH_SECRET),
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8), serverKeys, WebPushSender.decode(SALT));

        assertThat(WebPushSender.encode(body)).isEqualTo(BODY);
    }

    @Test
    void tellsWhetherTheTwoKeysBelongTogether() throws Exception {
        KeyPair keys = WebPushSender.newKeyPair();
        String publicKey = WebPushSender.encode(WebPushSender.raw((java.security.interfaces.ECPublicKey) keys.getPublic()));
        String privateKey = WebPushSender.encode(unsigned(((java.security.interfaces.ECPrivateKey) keys.getPrivate()).getS()));

        assertThat(new WebPushSender(new PushProperties(publicKey, privateKey, "https://fade.test"), Clock.systemUTC()).keysMatch()).isTrue();
        assertThat(new WebPushSender(new PushProperties(AS_PUBLIC, privateKey, "https://fade.test"), Clock.systemUTC()).keysMatch()).isFalse();
    }

    private static byte[] unsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }
}

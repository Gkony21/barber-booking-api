package gr.barber.booking.media;

import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.User;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BarberPhotoTest extends IntegrationTest {

    @Autowired
    private ImageStorage storage;

    private User barberUser;
    private Barber barber;

    @BeforeEach
    void setUp() {
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
    }

    @Test
    void uploadedPhotoIsShownPubliclyAsAResizedJpegWithoutMetadata() throws Exception {
        byte[] withGps = jpegWithExif(3000, 2000, "GPS 37.9838 N 23.7275 E");

        MvcResult result = upload("/barbers/me/photos", withGps)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(startsWith("/media/")))
                .andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.url");

        mockMvc.perform(get("/barbers/{id}/photos", barber.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url").value(url));

        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", startsWith("max-age=31536000")))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(Math.max(image.getWidth(), image.getHeight())).isEqualTo(1600);
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS 37.98");
    }

    @Test
    void avatarIsASquareAndReplacingItDeletesTheOldFile() throws Exception {
        upload("/barbers/me/avatar", png(800, 600), "PUT").andExpect(status().isOk());
        String firstKey = keyOf(barberRepository.findById(barber.getId()).orElseThrow().getAvatarKey());

        String url = mockMvc.perform(get("/barbers/{id}", barber.getId()))
                .andExpect(jsonPath("$.avatarUrl").value("/media/" + firstKey))
                .andReturn().getResponse().getContentAsString();
        assertThat(url).isNotEmpty();

        BufferedImage avatar = ImageIO.read(storage.load(firstKey).orElseThrow().getInputStream());
        assertThat(avatar.getWidth()).isEqualTo(avatar.getHeight()).isEqualTo(512);

        upload("/barbers/me/avatar", png(400, 400), "PUT").andExpect(status().isOk());
        assertThat(storage.load(firstKey)).isEmpty();

        mockMvc.perform(delete("/barbers/me/avatar").header("Authorization", bearer(barberUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void filesThatAreNotImagesAreRefused() throws Exception {
        upload("/barbers/me/photos", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/barbers/{id}/photos", barber.getId())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void atMostTwentyPhotos() throws Exception {
        byte[] small = png(40, 40);
        for (int i = 0; i < 20; i++) {
            upload("/barbers/me/photos", small).andExpect(status().isCreated());
        }
        upload("/barbers/me/photos", small).andExpect(status().isBadRequest());
    }

    @Test
    void barbersCanOnlyDeleteTheirOwnPhotosAndDeletingRemovesTheFile() throws Exception {
        String body = upload("/barbers/me/photos", png(100, 100)).andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String key = keyOf(com.jayway.jsonpath.JsonPath.read(body, "$.url"));

        User other = createUser("Kostas", "kostas@test.gr", Role.BARBER);
        createApprovedBarber(other, true);
        mockMvc.perform(delete("/barbers/me/photos/{id}", id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/barbers/me/photos/{id}", id).header("Authorization", bearer(barberUser)))
                .andExpect(status().isNoContent());
        assertThat(storage.load(key)).isEmpty();
    }

    @Test
    void mediaUrlsCannotReachOtherFiles() throws Exception {
        // Spring Security already rejects encoded slashes (400); the key check is the second line of defence.
        mockMvc.perform(get("/media/{key}", "..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/media/{key}", "application.properties")).andExpect(status().isNotFound());
    }

    @Test
    void customersCannotUpload() throws Exception {
        User customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        mockMvc.perform(multipart("/barbers/me/photos")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png(10, 10)))
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions upload(String path, byte[] content) throws Exception {
        return upload(path, content, "POST");
    }

    private ResultActions upload(String path, byte[] content, String method) throws Exception {
        return mockMvc.perform(multipart(path)
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", content))
                .with(request -> {
                    request.setMethod(method);
                    return request;
                })
                .header("Authorization", bearer(barberUser)));
    }

    private static String keyOf(String urlOrKey) {
        return urlOrKey.replace("/media/", "");
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** A JPEG with an EXIF segment carrying a fake GPS note, as phone photos have. */
    private static byte[] jpegWithExif(int width, int height, String note) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        byte[] plain = jpeg.toByteArray();

        byte[] payload = ("Exif\0\0" + note).getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2); // SOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }
}

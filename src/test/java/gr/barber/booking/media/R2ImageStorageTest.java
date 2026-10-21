package gr.barber.booking.media;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

/** R2 speaks the S3 API, so S3Mock (an S3 stand-in in a container) takes its place in tests. */
@Testcontainers
class R2ImageStorageTest {

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:4.7.0")
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "photos")
            .withExposedPorts(9090);

    static R2ImageStorage storage;

    @BeforeAll
    static void connect() {
        String endpoint = "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
        storage = new R2ImageStorage(endpoint, "photos", "test-key", "test-secret");
    }

    @Test
    void storesLoadsAndDeletesPhotos() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

        String key = storage.save(jpeg);
        assertThat(LocalDiskImageStorage.isValidKey(key)).isTrue();
        assertThat(storage.load(key).orElseThrow().getInputStream().readAllBytes()).isEqualTo(jpeg);

        storage.delete(key);
        assertThat(storage.load(key)).isEmpty();
    }

    // Only keys the app generated are looked up, as on disk.
    @Test
    void refusesKeysItDidNotGenerate() {
        assertThat(storage.load("../secrets.txt")).isEmpty();
        assertThat(storage.load("0b2a8d3e-0000-4000-8000-000000000000.jpg")).isEmpty();
    }
}

package gr.barber.booking.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Photos in Cloudflare R2 (app.storage.type=r2), which speaks the S3 API. Servers on hosts like
 * Render lose their disk on every deploy, so photos must live here in production. They are still
 * served through /media/{key}, so URLs and caching stay the same as on a laptop.
 */
@Component
@ConditionalOnProperty(value = "app.storage.type", havingValue = "r2")
public class R2ImageStorage implements ImageStorage {

    private final S3Client s3;
    private final String bucket;

    public R2ImageStorage(@Value("${app.storage.r2.endpoint}") String endpoint,
                          @Value("${app.storage.r2.bucket}") String bucket,
                          @Value("${app.storage.r2.access-key-id}") String accessKeyId,
                          @Value("${app.storage.r2.secret-access-key}") String secretAccessKey) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public String save(byte[] jpeg) {
        String key = UUID.randomUUID() + ".jpg";
        s3.putObject(request -> request.bucket(bucket).key(key).contentType("image/jpeg"), RequestBody.fromBytes(jpeg));
        return key;
    }

    @Override
    public Optional<Resource> load(String key) {
        // Same rule as on disk: only keys this app generated are looked up.
        if (!LocalDiskImageStorage.isValidKey(key)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new InputStreamResource(s3.getObject(request -> request.bucket(bucket).key(key))));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        if (LocalDiskImageStorage.isValidKey(key)) {
            s3.deleteObject(request -> request.bucket(bucket).key(key));
        }
    }
}

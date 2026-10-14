package gr.barber.booking.media;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;

import java.time.Duration;

@RestController
public class MediaController {

    private final ImageStorage storage;

    public MediaController(ImageStorage storage) {
        this.storage = storage;
    }

    // Keys are random and never reused, so browsers may cache an image forever.
    @GetMapping("/media/{key}")
    public ResponseEntity<Resource> image(@PathVariable String key) {
        return storage.load(key)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static String url(String key) {
        return key == null ? null : "/media/" + key;
    }
}

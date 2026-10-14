package gr.barber.booking.media;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Where processed images are kept. Locally a folder on disk; on a server this can be swapped
 * for object storage without touching the rest of the app.
 */
public interface ImageStorage {

    /** Stores a JPEG and returns its key. */
    String save(byte[] jpeg);

    Optional<Resource> load(String key);

    void delete(String key);
}

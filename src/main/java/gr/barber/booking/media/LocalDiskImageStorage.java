package gr.barber.booking.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class LocalDiskImageStorage implements ImageStorage {

    // Keys are generated here, so anything else (e.g. "../") is refused before touching the disk.
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");

    private final Path directory;

    public LocalDiskImageStorage(@Value("${app.storage.local-dir:uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String save(byte[] jpeg) {
        String key = UUID.randomUUID() + ".jpg";
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(key), jpeg);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store image", e);
        }
        return key;
    }

    @Override
    public Optional<Resource> load(String key) {
        if (!isValidKey(key)) {
            return Optional.empty();
        }
        Path file = directory.resolve(key);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void delete(String key) {
        if (!isValidKey(key)) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete image", e);
        }
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }
}

package gr.barber.booking.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    // Big uploads are read at reduced size (to save memory), yet still come out at full target size.
    @Test
    void largeImagesAreScaledToTheTargetSize() throws IOException {
        byte[] upload = png(7000, 3000);

        BufferedImage photo = read(processor.photo(upload, 1600));
        assertThat(photo.getWidth()).isEqualTo(1600);
        assertThat(photo.getHeight()).isBetween(685, 686);

        BufferedImage avatar = read(processor.square(upload, 512));
        assertThat(avatar.getWidth()).isEqualTo(512);
        assertThat(avatar.getHeight()).isEqualTo(512);
    }

    @Test
    void smallImagesAreNotEnlarged() throws IOException {
        BufferedImage photo = read(processor.photo(png(800, 600), 1600));
        assertThat(photo.getWidth()).isEqualTo(800);
        assertThat(photo.getHeight()).isEqualTo(600);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }
}

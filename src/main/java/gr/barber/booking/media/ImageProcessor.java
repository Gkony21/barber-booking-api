package gr.barber.booking.media;

import gr.barber.booking.exception.BadRequestException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.IntBinaryOperator;

/**
 * Turns an upload into a clean JPEG. Decoding proves the file really is an image, and drawing
 * it onto a new canvas drops all metadata — including the GPS location phones store in photos.
 */
@Component
public class ImageProcessor {

    private static final int MAX_SOURCE_SIDE = 10_000; // refuse "decompression bombs" before decoding
    private static final float QUALITY = 0.85f;

    /** Scales down so the longest side is at most {@code maxSide}. */
    public byte[] photo(byte[] upload, int maxSide) {
        BufferedImage source = decode(upload, (width, height) -> Math.max(width, height), maxSide);
        double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return encode(draw(source, 0, 0, source.getWidth(), source.getHeight(), width, height));
    }

    /** Crops the centre square and scales it to {@code side}×{@code side}. */
    public byte[] square(byte[] upload, int side) {
        BufferedImage source = decode(upload, Math::min, side);
        int crop = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - crop) / 2;
        int y = (source.getHeight() - crop) / 2;
        int target = Math.min(side, crop);
        return encode(draw(source, x, y, crop, crop, target, target));
    }

    /**
     * Decodes the image, skipping pixels while reading when it is much bigger than needed: a 48 MP
     * phone photo would take ~200 MB of memory at full size, enough to bring down a small server.
     * The side that matters ({@code measure}) is still read at 1–2× {@code target}, so quality holds.
     */
    private BufferedImage decode(byte[] upload, IntBinaryOperator measure, int target) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BadRequestException("NOT_AN_IMAGE", "The file is not a JPEG or PNG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > MAX_SOURCE_SIDE || reader.getHeight(0) > MAX_SOURCE_SIDE) {
                    throw new BadRequestException("IMAGE_TOO_LARGE", "The image is too large");
                }
                int side = measure.applyAsInt(reader.getWidth(0), reader.getHeight(0));
                ImageReadParam params = reader.getDefaultReadParam();
                if (side >= 2 * target) {
                    int step = (side + 2 * target - 1) / (2 * target);
                    params.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, params);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("NOT_AN_IMAGE", "The file is not a JPEG or PNG image");
        }
    }

    private static BufferedImage draw(BufferedImage source, int x, int y, int w, int h, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE); // transparent PNG areas become white, not black
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

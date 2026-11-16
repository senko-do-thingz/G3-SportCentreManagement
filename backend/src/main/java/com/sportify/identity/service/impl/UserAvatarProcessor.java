package com.sportify.identity.service.impl;

import com.sportify.core.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates, center crops, resizes to 256x256, and converts avatar uploads to WebP.
 */
@Component
public class UserAvatarProcessor {

    public static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    public static final int MIN_DIMENSION = 128;
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_PIXELS = 16_000_000L;
    public static final int OUTPUT_SIZE = 256;
    public static final String OUTPUT_CONTENT_TYPE = "image/webp";

    private static final Set<String> SUPPORTED_FORMATS = Set.of("jpeg", "jpg", "png", "webp");

    /**
     * Validates and processes avatar image data into a 256x256 square WebP image.
     */
    public byte[] process(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        if (data.length > MAX_AVATAR_BYTES) {
            throw new BusinessRuleException("Photo must be at most 5 MB");
        }

        validateSignature(data);

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidPhoto();
            }
            ImageReader reader = readers.next();
            BufferedImage original;
            int width;
            int height;
            try {
                String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!SUPPORTED_FORMATS.contains(formatName)) {
                    throw invalidPhoto();
                }
                reader.setInput(input, true, false);
                width = reader.getWidth(0);
                height = reader.getHeight(0);

                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new BusinessRuleException("Photo must be at most 4096 pixels per side and 16 megapixels");
                }
                if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
                    throw new BusinessRuleException("Image dimensions must be at least 128x128 pixels");
                }

                original = reader.read(0);
                if (original == null) {
                    throw invalidPhoto();
                }
            } finally {
                reader.dispose();
            }

            // Crop to center square
            int side = Math.min(width, height);
            int x = (width - side) / 2;
            int y = (height - side) / 2;
            BufferedImage cropped = original.getSubimage(x, y, side, side);

            // Resize to 256 x 256
            int imageType = original.getColorModel().hasAlpha()
                    ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB;
            BufferedImage resized = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, imageType);
            Graphics2D g = resized.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(cropped, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
            } finally {
                g.dispose();
            }

            // Encode to WebP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(resized, "webp", baos);
            if (!written) {
                throw new BusinessRuleException("Failed to encode image to WebP format");
            }
            return baos.toByteArray();
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw invalidPhoto();
        }
    }

    private static void validateSignature(byte[] data) {
        if (isJpeg(data) || isPng(data) || isWebp(data)) {
            return;
        }
        throw invalidPhoto();
    }

    private static boolean isJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 'P'
                && data[2] == 'N'
                && data[3] == 'G'
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    private static boolean isWebp(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static BusinessRuleException invalidPhoto() {
        return new BusinessRuleException("Photo must be a valid JPEG, PNG or WebP image");
    }
}

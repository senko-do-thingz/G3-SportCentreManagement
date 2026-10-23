package com.sportify.identity.service.impl;

import com.sportify.core.exception.BusinessRuleException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** Validates actual image content before storage, without trusting the multipart name or type. */
final class CoachPhotoValidator {
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16_000_000;

    private CoachPhotoValidator() {
    }

    static String validate(byte[] data) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidPhoto();
            ImageReader reader = readers.next();
            try {
                String type = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> throw invalidPhoto();
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Bound allocation before decoding. Home uploads are normally resized to 640 x 640.
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new BusinessRuleException("Photo must be at most 4096 pixels per side and 16 megapixels");
                }
                if (reader.read(0) == null) throw invalidPhoto();
                return type;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw invalidPhoto();
        }
    }

    private static BusinessRuleException invalidPhoto() {
        return new BusinessRuleException("Photo must be a valid JPEG, PNG or WebP image");
    }
}

package com.sportify.identity.service.impl;

import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoachPhotoValidatorTest {
    static byte[] image(String format, int width, int height) {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void decodesRealJpegPngAndWebpContent() {
        assertEquals("image/jpeg", CoachPhotoValidator.validate(image("jpeg", 2, 2)));
        assertEquals("image/png", CoachPhotoValidator.validate(image("png", 2, 2)));
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        assertEquals("image/webp", CoachPhotoValidator.validate(webp));
    }

    @Test
    void rejectsTruncatedSignaturesTextAndUnsupportedImages() {
        for (byte[] invalid : new byte[][] {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII),
                "<svg onload='alert(1)'/>".getBytes(StandardCharsets.US_ASCII),
                image("gif", 2, 2), image("bmp", 2, 2)}) {
            assertThrows(BusinessRuleException.class, () -> CoachPhotoValidator.validate(invalid));
        }
    }

    @Test
    void rejectsExcessiveDimensionsBeforePixelDecode() {
        assertThrows(BusinessRuleException.class, () -> CoachPhotoValidator.validate(image("png", 4097, 1)));
    }
}

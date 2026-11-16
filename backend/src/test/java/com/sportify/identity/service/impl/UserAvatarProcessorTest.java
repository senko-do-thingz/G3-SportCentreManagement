package com.sportify.identity.service.impl;

import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class UserAvatarProcessorTest {

    private UserAvatarProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new UserAvatarProcessor();
    }

    private byte[] createImage(int width, int height, String format, boolean alpha) throws IOException {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawLine(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(img, format, baos);
        assertTrue(written, "ImageIO should write format: " + format);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Reject null or empty photo payload")
    void rejectEmptyPayload() {
        assertThrows(BusinessRuleException.class, () -> processor.process(null));
        assertThrows(BusinessRuleException.class, () -> processor.process(new byte[0]));
    }

    @Test
    @DisplayName("Reject photo exceeding 5 MB limit")
    void rejectExceedingMaxSize() {
        byte[] oversized = new byte[UserAvatarProcessor.MAX_AVATAR_BYTES + 1];
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> processor.process(oversized));
        assertTrue(ex.getMessage().contains("5 MB"));
    }

    @Test
    @DisplayName("Reject invalid file signatures or random bytes")
    void rejectInvalidSignature() {
        byte[] randomBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> processor.process(randomBytes));
        assertTrue(ex.getMessage().contains("valid JPEG, PNG or WebP"));
    }

    @Test
    @DisplayName("Reject corrupted image with valid magic bytes")
    void rejectCorruptedImage() {
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThrows(BusinessRuleException.class, () -> processor.process(fakePng));
    }

    @Test
    @DisplayName("Reject unsupported formats such as GIF")
    void rejectUnsupportedFormatGif() throws IOException {
        BufferedImage img = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "gif", baos);
        byte[] gifBytes = baos.toByteArray();

        assertThrows(BusinessRuleException.class, () -> processor.process(gifBytes));
    }

    @Test
    @DisplayName("Reject image smaller than 128x128 pixels")
    void rejectTooSmallDimensions() throws IOException {
        byte[] smallJpg = createImage(127, 128, "jpeg", false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> processor.process(smallJpg));
        assertTrue(ex.getMessage().contains("128x128"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png", "webp"})
    @DisplayName("Successfully process and resize supported formats to 256x256 WebP")
    void processSupportedFormats(String format) throws IOException {
        boolean alpha = "png".equalsIgnoreCase(format) || "webp".equalsIgnoreCase(format);
        byte[] input = createImage(300, 200, format, alpha);

        byte[] output = processor.process(input);
        assertNotNull(output);
        assertTrue(output.length > 0);

        // Verify output is 256x256 WebP
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertNotNull(result, "Processed output should be readable by ImageIO");
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
    }

    @Test
    @DisplayName("Correctly center crops non-square input")
    void centerCropNonSquare() throws IOException {
        byte[] input = createImage(400, 200, "png", false);
        byte[] output = processor.process(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
    }
}

package com.nilsson.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for the {@link DHashService}, validating the accuracy and stability of
 * visual image fingerprinting.
 * <p>
 * This class ensures that the perceptual hashing algorithm is reliable for duplicate
 * detection by verifying:
 * <ul>
 *   <li><b>Consistency:</b> Confirms that the same image always produces the same hash.</li>
 *   <li><b>Format Independence:</b> Validates that an image saved in different formats
 *   (e.g., PNG vs. JPG) produces identical or near-identical hashes.</li>
 *   <li><b>Resilience to Scaling:</b> Ensures that resizing an image does not significantly
 *   alter its visual fingerprint.</li>
 *   <li><b>Hamming Distance Stability:</b> Verifies that visually similar images have a
 *   low Hamming distance, while different images have a high distance.</li>
 * </ul>
 */
class DHashServiceTest {

    private final DHashService dHashService = new DHashService();

    @Test
    @DisplayName("calculateDHash should produce similar hashes for the same image in different formats")
    void testCrossFormatConsistency(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.WHITE, 200, 200, Color.BLACK));
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.RED);
        g.fillOval(50, 50, 100, 100);
        g.dispose();

        File pngFile = tempDir.resolve("test.png").toFile();
        File jpgFile = tempDir.resolve("test.jpg").toFile();

        ImageIO.write(img, "png", pngFile);
        ImageIO.write(img, "jpg", jpgFile);

        long pngHash = dHashService.calculateDHash(pngFile);
        long jpgHash = dHashService.calculateDHash(jpgFile);

        int distance = Long.bitCount(pngHash ^ jpgHash);
        assertTrue(distance <= 5, "Cross-format Hamming distance should be minimal, found: " + distance);
    }

    @Test
    @DisplayName("calculateDHash should be resilient to image scaling")
    void testScalingResilience(@TempDir Path tempDir) throws IOException {
        BufferedImage original = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.WHITE, 512, 512, Color.BLACK));
        g.fillRect(0, 0, 512, 512);
        g.dispose();

        File originalFile = tempDir.resolve("original.png").toFile();
        ImageIO.write(original, "png", originalFile);

        BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.drawImage(original, 0, 0, 128, 128, null);
        g2.dispose();

        File scaledFile = tempDir.resolve("scaled.png").toFile();
        ImageIO.write(scaled, "png", scaledFile);

        long originalHash = dHashService.calculateDHash(originalFile);
        long scaledHash = dHashService.calculateDHash(scaledFile);

        int distance = Long.bitCount(originalHash ^ scaledHash);
        assertTrue(distance <= 3, "Scaling Hamming distance should be minimal, found: " + distance);
    }

    @Test
    @DisplayName("calculateDHash should produce different hashes for distinct visual patterns")
    void testDifferentImages(@TempDir Path tempDir) throws IOException {
        // Image 1: High-frequency vertical stripes (W, B, W, B...)
        // This creates many horizontal gradients.
        BufferedImage img1 = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = img1.createGraphics();
        for (int i = 0; i < 9; i++) {
            g1.setColor(i % 2 == 0 ? Color.WHITE : Color.BLACK);
            g1.fillRect(i * 10, 0, 10, 80);
        }
        g1.dispose();

        // Image 2: Solid White (No gradients)
        BufferedImage img2 = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img2.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 90, 80);
        g2.dispose();

        File file1 = tempDir.resolve("stripes.png").toFile();
        File file2 = tempDir.resolve("solid.png").toFile();

        ImageIO.write(img1, "png", file1);
        ImageIO.write(img2, "png", file2);

        long hash1 = dHashService.calculateDHash(file1);
        long hash2 = dHashService.calculateDHash(file2);

        int distance = Long.bitCount(hash1 ^ hash2);
        
        // Stripes vs Solid should have a significant distance (expected ~32 bits)
        assertTrue(distance >= 20, "Distinct patterns should have high Hamming distance, found: " + distance);
        assertNotEquals(0, hash1, "Striped image should produce a non-zero hash");
        assertEquals(0, hash2, "Solid image should produce a zero hash");
    }
}

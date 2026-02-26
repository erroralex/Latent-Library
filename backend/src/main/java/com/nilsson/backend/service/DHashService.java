package com.nilsson.backend.service;

import com.nilsson.backend.exception.ImageProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Service for calculating perceptual difference hashes (dHash) for images.
 * <p>
 * The dHash algorithm is used to identify visually similar images by creating a fingerprint
 * based on the relative gradients of pixel intensities. Unlike cryptographic hashes,
 * perceptual hashes remain stable even if the image is slightly resized, compressed,
 * or undergoes minor color adjustments. This implementation is hardened to handle
 * cross-format comparisons (e.g., PNG vs JPG) by standardizing the color space
 * and removing alpha channels before processing.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Perceptual Fingerprinting:</b> Generates a 64-bit hash representing the visual structure of an image.</li>
 *   <li><b>Format Standardization:</b> Normalizes images to a consistent color space (RGB) and size (9x8) to ensure
 *   reliable comparisons across different file formats.</li>
 *   <li><b>Resilience:</b> Employs bicubic interpolation and anti-aliasing during resizing to maintain
 *   structural integrity for hashing.</li>
 * </ul>
 */
@Service
public class DHashService {

    private static final Logger log = LoggerFactory.getLogger(DHashService.class);

    public long calculateDHash(File file) {
        try {
            BufferedImage rawImage = ImageIO.read(file);
            if (rawImage == null) {
                log.debug("Failed to decode image for hashing (unsupported or corrupt): {}", file.getName());
                return 0;
            }

            BufferedImage standardImage = new BufferedImage(rawImage.getWidth(), rawImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D gStandard = standardImage.createGraphics();
            gStandard.drawImage(rawImage, 0, 0, null);
            gStandard.dispose();

            BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D gResized = resized.createGraphics();
            gResized.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gResized.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            gResized.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            gResized.drawImage(standardImage, 0, 0, 9, 8, null);
            gResized.dispose();

            long hash = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int leftPixel = resized.getRGB(x, y) & 0xFF;
                    int rightPixel = resized.getRGB(x + 1, y) & 0xFF;

                    if (leftPixel > rightPixel) {
                        hash |= (1L << (y * 8 + x));
                    }
                }
            }

            return hash;
        } catch (Exception e) {
            log.debug("Error calculating dHash for file: {} - {}", file.getName(), e.getMessage());
            return 0;
        }
    }
}

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
 * or undergoes minor color adjustments.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Image Normalization:</b> Resizes images to a standard 9x8 grayscale grid to
 *   eliminate high-frequency noise and ensure consistent comparison.</li>
 *   <li><b>Gradient Calculation:</b> Compares adjacent pixels horizontally to determine
 *   the direction of the intensity gradient, producing a 64-bit bitmask.</li>
 *   <li><b>Perceptual Fingerprinting:</b> Generates a {@code long} value that represents
 *    the image's visual structure, suitable for Hamming distance calculations.</li>
 * </ul>
 */
@Service
public class DHashService {

    private static final Logger logger = LoggerFactory.getLogger(DHashService.class);

    /**
     * Calculates the dHash of an image file.
     *
     * @param file
     *         The image file to hash.
     *
     * @return The 64-bit dHash as a long.
     *
     * @throws ImageProcessingException
     *         If the image cannot be read or processed.
     */
    public long calculateDHash(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new ImageProcessingException("Failed to decode image for hashing: " + file.getName());
            }

            // 1. Resize to 9x8 (width x height)
            BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, 9, 8, null);
            g.dispose();

            // 2. Calculate hash
            long hash = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    // getRGB on TYPE_BYTE_GRAY returns grayscale value in all components
                    int leftPixel = resized.getRGB(x, y) & 0xFF;
                    int rightPixel = resized.getRGB(x + 1, y) & 0xFF;

                    if (leftPixel > rightPixel) {
                        hash |= (1L << (y * 8 + x));
                    }
                }
            }

            return hash;
        } catch (IOException e) {
            logger.error("IO error calculating dHash for file: {}", file.getAbsolutePath(), e);
            throw new ImageProcessingException("Failed to calculate dHash", e);
        }
    }
}

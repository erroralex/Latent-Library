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
 *   <li><b>Image Normalization:</b> Standardizes images to a consistent RGB color space
 *   to eliminate alpha-channel interference.</li>
 *   <li><b>High-Quality Resizing:</b> Scales images to a 9x8 grayscale grid using
 *   bicubic interpolation to preserve structural gradients.</li>
 *   <li><b>Gradient Calculation:</b> Compares adjacent pixels horizontally to determine
 *   the direction of the intensity gradient, producing a 64-bit bitmask.</li>
 *   <li><b>Perceptual Fingerprinting:</b> Generates a {@code long} value that represents
 *   the image's visual structure, suitable for Hamming distance calculations.</li>
 * </ul>
 */
@Service
public class DHashService {

    private static final Logger log = LoggerFactory.getLogger(DHashService.class);

    public long calculateDHash(File file) {
        try {
            BufferedImage rawImage = ImageIO.read(file);
            if (rawImage == null) {
                throw new ImageProcessingException("Failed to decode image for hashing: " + file.getName());
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
        } catch (IOException e) {
            log.error("IO error calculating dHash for file: {}", file.getAbsolutePath(), e);
            throw new ImageProcessingException("Failed to calculate dHash", e);
        }
    }
}

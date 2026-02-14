package com.nilsson.backend.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.exception.ImageProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

/**
 * Service for performing AI-based image tagging using the WD14 ONNX model.
 * <p>
 * This service provides automated image interrogation by running inference on a pre-trained
 * ConvNext-based model (WD14). it handles the entire inference pipeline, including image
 * preprocessing (resizing, padding, and normalization), ONNX session management, and
 * post-processing of model outputs into human-readable tags.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Model Inference:</b> Manages the {@link OrtSession} and executes the WD14 model
 *   to predict descriptive tags for given images.</li>
 *   <li><b>Image Preprocessing:</b> Normalizes input images to the required 448x448 BGR
 *   format with letterboxing to maintain aspect ratio.</li>
 *   <li><b>Tag Mapping:</b> Loads and parses the associated CSV mapping to translate
 *   model output indices into semantic tags.</li>
 *   <li><b>Threshold Filtering:</b> Filters predicted tags based on a configurable
 *   confidence threshold to ensure high-quality results.</li>
 * </ul>
 */
@Service
public class ImageTaggerService {

    private static final Logger logger = LoggerFactory.getLogger(ImageTaggerService.class);
    private static final int MODEL_SIZE = 448;

    private final TaggerModelService modelService;
    private OrtEnvironment env;
    private OrtSession session;
    private List<String> tags;

    public ImageTaggerService(TaggerModelService modelService) {
        this.modelService = modelService;
    }

    private synchronized void initSession() {
        if (session != null) return;
        if (!modelService.isModelReady()) {
            throw new ApplicationException("Tagging model is not downloaded yet.");
        }

        try {
            if (env == null) env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelService.getModelPath().toString(), new OrtSession.SessionOptions());
            loadTags();
            logger.info("WD14 ONNX session initialized.");
        } catch (OrtException e) {
            throw new ApplicationException("Failed to initialize ONNX session: " + e.getMessage(), e);
        }
    }

    private void loadTags() {
        tags = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(modelService.getTagsPath().toFile()))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    tags.add(parts[1]);
                }
            }
        } catch (IOException e) {
            throw new ApplicationException("Failed to load tags CSV: " + e.getMessage(), e);
        }
    }

    public List<String> tagImage(File imageFile, float threshold) {
        initSession();

        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) throw new ImageProcessingException("Could not decode image: " + imageFile.getName());

            float[] floatData = preprocessImage(img);
            long[] shape = new long[]{1, MODEL_SIZE, MODEL_SIZE, 3};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), shape)) {
                Map<String, OnnxTensor> inputs = Map.of(session.getInputNames().iterator().next(), inputTensor);

                try (OrtSession.Result results = session.run(inputs)) {
                    float[][] output = (float[][]) results.get(0).getValue();
                    return parseOutput(output[0], threshold);
                }
            }

        } catch (IOException | OrtException e) {
            logger.error("Inference failed for: {}", imageFile.getName(), e);
            throw new ImageProcessingException("AI Tagging failed: " + e.getMessage(), e);
        }
    }

    private float[] preprocessImage(BufferedImage original) {
        BufferedImage resized = new BufferedImage(MODEL_SIZE, MODEL_SIZE, BufferedImage.TYPE_INT_BGR);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MODEL_SIZE, MODEL_SIZE);

        double scale = Math.min((double) MODEL_SIZE / original.getWidth(), (double) MODEL_SIZE / original.getHeight());
        int w = (int) (original.getWidth() * scale);
        int h = (int) (original.getHeight() * scale);
        int x = (MODEL_SIZE - w) / 2;
        int y = (MODEL_SIZE - h) / 2;

        g.drawImage(original, x, y, w, h, null);
        g.dispose();

        float[] data = new float[MODEL_SIZE * MODEL_SIZE * 3];
        int[] pixels = resized.getRGB(0, 0, MODEL_SIZE, MODEL_SIZE, null, 0, MODEL_SIZE);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            float red = (pixel >> 16) & 0xFF;
            float green = (pixel >> 8) & 0xFF;
            float blue = pixel & 0xFF;

            int base = i * 3;
            data[base] = blue;
            data[base + 1] = green;
            data[base + 2] = red;
        }
        return data;
    }

    private List<String> parseOutput(float[] probabilities, float threshold) {
        List<String> resultTags = new ArrayList<>();
        for (int i = 4; i < probabilities.length && i < tags.size(); i++) {
            if (probabilities[i] >= threshold) {
                resultTags.add(tags.get(i));
            }
        }
        return resultTags;
    }
}

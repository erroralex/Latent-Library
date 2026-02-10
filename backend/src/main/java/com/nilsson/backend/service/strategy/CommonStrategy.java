package com.nilsson.backend.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standardized parsing implementation for image generation metadata, primarily targeting the Automatic1111/Forge format.
 * <p>
 * This strategy is the most versatile, capable of processing both raw text blocks (the "Parameters" chunk)
 * and structured JSON input. It implements complex regex-based parsing to separate positive prompts,
 * negative prompts, and technical parameters (Steps, Sampler, CFG, etc.). It also includes specialized
 * logic for extracting LoRA tags and strengths directly from the prompt text.
 * <p>
 * Key functionalities:
 * - Text Block Decomposition: Splits raw metadata into Prompt, Negative Prompt, and Parameter sections.
 * - Regex Parameter Extraction: Dynamically identifies key-value pairs in the parameter block.
 * - LoRA Discovery: Scans prompt text for {@code <lora:...>} tags to populate the LoRAs metadata field.
 * - JSON Normalization: Maps common JSON keys (e.g., "cfgscale", "sampler_name") to standard application attributes.
 * - Dimension Parsing: Resolves "Size" strings (e.g., "512x512") into discrete Width and Height attributes.
 */
@Service
public class CommonStrategy implements MetadataStrategy {

    @Override
    public Map<String, String> parse(String text) {
        Map<String, String> results = new HashMap<>();

        String[] parts = text.split("Negative prompt:");
        String positivePrompt = parts[0].trim();
        results.put("Prompt", positivePrompt);

        String remaining = "";
        if (parts.length > 1) {
            String[] negAndParams = parts[1].split("\nSteps: ");
            results.put("Negative", negAndParams[0].trim());

            if (negAndParams.length > 1) {
                remaining = "Steps: " + negAndParams[1];
            } else {
                int lastSteps = parts[1].lastIndexOf("\nSteps:");
                if (lastSteps != -1) {
                    results.put("Negative", parts[1].substring(0, lastSteps).trim());
                    remaining = parts[1].substring(lastSteps + 1);
                } else {
                    remaining = parts[1];
                }
            }
        } else {
            int stepsIndex = text.lastIndexOf("\nSteps:");
            if (stepsIndex != -1) {
                results.put("Prompt", text.substring(0, stepsIndex).trim());
                remaining = text.substring(stepsIndex + 1);
            } else {
                remaining = text;
            }
        }

        extractLorasFromText(positivePrompt, results);

        Pattern paramPattern = Pattern.compile("([^:,]+):\\s*([^,]+)(?:,|$)");
        Matcher matcher = paramPattern.matcher(remaining);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String lowerKey = key.toLowerCase();
            String value = matcher.group(2).trim();

            switch (lowerKey) {
                case "steps" -> results.put("Steps", value);
                case "sampler" -> results.put("Sampler", value);
                case "schedule type", "scheduler" -> results.put("Scheduler", value);
                case "cfg scale", "cfg" -> results.put("CFG", value);
                case "distilled cfg scale", "distilled cfg" -> results.put("Distilled CFG", value);
                case "seed" -> results.put("Seed", value);
                case "size" -> {
                    String[] dim = value.split("x");
                    if (dim.length == 2) {
                        results.put("Width", dim[0]);
                        results.put("Height", dim[1]);
                    }
                }
                case "model" -> results.put("Model", value);
                case "model hash" -> results.put("Model Hash", value);
                case "denoising strength" -> results.put("Denoise", value);
                case "hires upscale" -> results.put("Hires. fix", "Enabled (" + value + "x)");
                case "lora hashes" -> {
                    if (!results.containsKey("Loras")) {
                        results.put("Loras", value);
                    }
                }
            }
        }

        if (results.containsKey("Distilled CFG")) {
            String cfg = results.get("CFG");
            String dist = results.get("Distilled CFG");
            if (cfg != null) {
                results.put("CFG", cfg + " (distilled " + dist + ")");
            } else {
                results.put("CFG", dist + " (distilled)");
            }
            results.remove("Distilled CFG");
        }

        return results;
    }

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        if (!value.isValueNode()) return;
        String text = value.asText().trim();
        if (text.isEmpty()) return;

        if (key.equals("steps")) results.put("Steps", text);
        else if (key.equals("seed") || key.equals("noise_seed")) results.put("Seed", text);
        else if (key.equals("cfg") || key.equals("cfgscale")) results.put("CFG", text);
        else if (key.equals("sampler_name")) results.put("Sampler", text);
        else if (key.equals("scheduler")) results.put("Scheduler", text);

        else if (key.equals("width")) {
            if (isValidSize(text)) results.put("Width", text);
        } else if (key.equals("height")) {
            if (isValidSize(text)) results.put("Height", text);
        } else if (key.contains("lora_name")) {
            String existing = results.getOrDefault("Loras", "");
            if (!existing.contains(text)) {
                results.put("Loras", existing.isEmpty() ? text : existing + ", " + text);
            }
        } else if (key.equals("upscale_by") || key.equals("upscale_method")) {
            results.put("Hires. fix", "Enabled (" + text + "x)");
        } else if (key.contains("control_net") || key.contains("controlnet")) {
            String existing = results.getOrDefault("ControlNet", "");
            if (!existing.contains(text)) {
                results.put("ControlNet", existing.isEmpty() ? text : existing + ", " + text);
            }
        }
    }

    private void extractLorasFromText(String prompt, Map<String, String> results) {
        Pattern loraPattern = Pattern.compile(
                "<lora:([^:>]+)(?::([^:>]+))?(?::([^:>]+))?>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = loraPattern.matcher(prompt);
        StringBuilder loraBuilder = new StringBuilder();

        while (m.find()) {
            String name = m.group(1);
            String strength = m.group(2);

            if (loraBuilder.length() > 0) loraBuilder.append(", ");
            loraBuilder.append("<lora:").append(name);

            if (strength != null) {
                loraBuilder.append(":").append(strength);
            }
            loraBuilder.append(">");
        }

        if (loraBuilder.length() > 0) {
            results.put("Loras", loraBuilder.toString());
        }
    }

    private boolean isValidSize(String text) {
        try {
            int val = Integer.parseInt(text);
            return val > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

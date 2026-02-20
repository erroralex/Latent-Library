package com.nilsson.backend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.nilsson.backend.exception.ApplicationException;
import com.nilsson.backend.service.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata extraction strategy for ComfyUI workflows.
 * <p>
 * This strategy implements a sophisticated graph-traversal algorithm to resolve generation
 * parameters from ComfyUI's node-based architecture. It supports both the "UI Schema"
 * (exported from the web interface) and the "API Schema" (execution graph). It recursively
 * traces connections between nodes (e.g., from a KSampler back to its CLIPTextEncode inputs)
 * to accurately identify positive prompts, negative prompts, models, and samplers.
 * <p>
 * Key functionalities:
 * - Graph Traversal: Recursively resolves linked parameters by following node input/output links.
 * - Schema Detection: Automatically distinguishes between UI-based workflows and API-based graphs.
 * - Intelligent Filtering: Uses extensive blacklists and whitelists to ignore utility nodes (reroutes,
 * switches, pipes) and focus on core generation parameters.
 * - LoRA Extraction: Identifies LoRAs from both specialized loader nodes and embedded prompt tags.
 * - Parameter Normalization: Resolves and formats technical values like CFG, Seed, and Sampler names.
 */
@Service
public class ComfyUIStrategy implements MetadataStrategy {

    private static final Logger log = LoggerFactory.getLogger(ComfyUIStrategy.class);

    private final UserDataManager userDataManager;

    public ComfyUIStrategy(@Lazy UserDataManager userDataManager) {
        this.userDataManager = userDataManager;
    }

    // Default constructor for manual instantiation if needed, though Spring injection is preferred
    public ComfyUIStrategy() {
        this.userDataManager = null;
    }

    private static final Set<String> VALID_EXTENSIONS = Set.of(
            ".safetensors", ".ckpt", ".gguf", ".pt", ".pth", ".bin"
    );

    private static final Set<String> IGNORED_FILENAME_PATTERNS = Set.of(
            "upscale", "esrgan", "controlnet", "ipadapter", "faceid", "adapter",
            "clip", "vae", "preview", "t5", "encoder", "refiner",
            "bbox", "yolo", "ultralytics", "mediapipe", "segs", "detailer", "mask", "inpaint"
    );

    private static final Set<String> ALLOWED_MODEL_NODE_TYPES = Set.of(
            "checkpoint", "unet", "loader", "lora"
    );

    private static final Set<String> IGNORED_MODEL_NODE_TYPES = Set.of(
            "preprocessor", "detailer", "output", "save image", "preview image",
            "save", "preview", "detector", "mask"
    );

    private static final Set<String> SAMPLER_KEYWORDS = Set.of(
            "euler", "heun", "dpm", "lms", "ddim", "uni_pc", "lcm",
            "multistep", "singlestep", "clownshark"
    );

    private static final Set<String> SCHEDULER_KEYWORDS = Set.of(
            "normal", "karras", "exponential", "sgm", "simple", "beta",
            "ddim", "standard", "linear", "uniform", "gpu",
            "polyexponential", "automatic"
    );

    private static final Set<String> IGNORED_PROMPT_TEXTS = Set.of(
            "fixed", "increment", "decrement", "randomize", "random", "reproduce",
            "enable", "disable", "on", "off", "center", "resize", "crop",
            "Select Wildcard", "Full Cache", "Preserve", "Baked/Default",
            "auto", "bf16", "undefined", "null"
    );

    private static final Set<String> PASSTHROUGH_TYPES = Set.of(
            "reroute", "switch", "pipe", "bus", "node", "wifi",
            "set", "get", "any", "showtext", "pysssss",
            "stringreplace", "wildcard"
    );

    private static final Set<String> OUTPUT_NODE_TYPES = Set.of(
            "save image", "preview image", "saveimage", "previewimage",
            "video save", "save", "preview", "image save"
    );

    private static final Pattern LORA_TAG_PATTERN =
            Pattern.compile("<lora:([^:>]+)(?::([^:>]+))?.*?>");

    @Override
    public void extract(String key, JsonNode value, JsonNode parentNode, Map<String, String> results) {
        try {
            if (key.equalsIgnoreCase("nodes") && value.isArray()) {
                processNodes(value, parentNode.get("links"), results);
                if (parentNode.has("extra") && parentNode.get("extra").has("seed_widgets")) {
                    processGlobalSeedMap(parentNode.get("extra").get("seed_widgets"), value, results);
                }
            } else if (key.equals("api_nodes") && value.isObject()) {
                processApiWorkflow(value, results);
                results.put("_api_graph_analyzed", "true");
                results.put("_lock_core_params", "true");
            } else if (isNodeId(key) && value.has("class_type") && value.has("inputs")) {
                if (!results.containsKey("_api_graph_analyzed")) {
                    processApiWorkflow(parentNode, results);
                    results.put("_api_graph_analyzed", "true");
                    results.put("_lock_core_params", "true");
                }
            } else if (key.equalsIgnoreCase("inputs") && value.isObject()) {
                boolean skipCoreParams = results.containsKey("_api_graph_analyzed");
                processInputsBlock(value, parentNode, results, skipCoreParams);
            }
        } catch (Exception e) {
            log.error("Internal strategy error during ComfyUI graph navigation for key: {}", key, e);
            throw new ApplicationException("System failed to navigate ComfyUI generation graph.", e);
        }
    }

    private boolean isNodeId(String key) {
        return key.matches("\\d+");
    }

    private void processApiWorkflow(JsonNode root, Map<String, String> results) {
        JsonNode bestSampler = null;
        long maxSteps = -1;
        double fluxGuidance = -1;
        String directSchedulerFound = null;

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();
            String type = getNodeType(node).toLowerCase();

            if (node.has("inputs")) {
                processInputsBlock(node.get("inputs"), node, results, false);
                double g = resolveFloatParamRecursive(node, "guidance", root);
                if (g > -1) fluxGuidance = g;
                if (type.contains("power lora loader")) {
                    extractPowerLoras(node.get("inputs"), results);
                }
            }

            if (type.contains("scheduler") && node.has("inputs")) {
                JsonNode schedVal = node.get("inputs").get("scheduler");
                if (schedVal != null && schedVal.isTextual()) {
                    directSchedulerFound = schedVal.asText();
                }
            }

            long steps = -1;
            if (type.contains("samplercustom")) {
                JsonNode sigmasNode = getLinkedNodeApi(node, "sigmas", root);
                if (sigmasNode != null) {
                    steps = resolveNumericParamRecursive(sigmasNode, "steps", root);
                }
            } else if (type.contains("sampler") && !type.contains("detailer") && !type.contains("upscale")) {
                steps = resolveNumericParamRecursive(node, "steps", root);
            }

            if (steps > maxSteps) {
                maxSteps = steps;
                bestSampler = node;
            }
        }

        if (bestSampler == null) return;

        results.put("Steps", String.valueOf(maxSteps));

        long seed = resolveNumericParamRecursive(bestSampler, "seed", root);
        if (seed == -1) seed = resolveNumericParamRecursive(bestSampler, "noise_seed", root);
        if (seed == -1) {
            JsonNode noiseNode = getLinkedNodeApi(bestSampler, "noise", root);
            if (noiseNode != null) seed = resolveNumericParamRecursive(noiseNode, "noise_seed", root);
        }
        if (seed > -1) results.put("Seed", String.valueOf(seed));

        double cfg = resolveFloatParamRecursive(bestSampler, "cfg", root);
        if (cfg == -1) {
            JsonNode guiderNode = getLinkedNodeApi(bestSampler, "guider", root);
            if (guiderNode != null) cfg = resolveFloatParamRecursive(guiderNode, "cfg", root);
        }

        if (cfg > -1) {
            String cfgStr = formatDecimal(cfg);
            if (fluxGuidance > -1) cfgStr += " (distilled " + formatDecimal(fluxGuidance) + ")";
            results.put("CFG", cfgStr);
        }

        String sampler = resolveStringParamRecursive(bestSampler, "sampler_name", root);
        if (sampler == null) {
            JsonNode samplerNode = getLinkedNodeApi(bestSampler, "sampler", root);
            if (samplerNode != null) sampler = resolveStringParamRecursive(samplerNode, "sampler_name", root);
        }

        String scheduler = resolveStringParamRecursive(bestSampler, "scheduler", root);
        if (scheduler == null) {
            JsonNode sigNode = getLinkedNodeApi(bestSampler, "sigmas", root);
            if (sigNode != null) scheduler = resolveStringParamRecursive(sigNode, "scheduler", root);
        }

        if (scheduler == null && directSchedulerFound != null) scheduler = directSchedulerFound;

        if (sampler != null) results.put("Sampler", sampler);
        if (scheduler != null) results.put("Scheduler", scheduler);
    }

    private void processNodes(JsonNode nodes, JsonNode links, Map<String, String> results) {
        Map<Integer, Integer> linkMap = buildLinkMap(links);
        Map<Integer, JsonNode> nodeMap = buildNodeMap(nodes);

        JsonNode bestSampler = null;
        long maxSteps = -1;
        double fluxGuidance = -1;

        for (JsonNode node : nodes) {
            String type = getNodeType(node).toLowerCase();
            if (OUTPUT_NODE_TYPES.stream().anyMatch(type::contains) && isNodeActive(node)) {
                JsonNode sourceSampler = traceBackToSampler(node, nodeMap, linkMap, 0);
                if (sourceSampler != null) {
                    long steps = resolveNumericParam(sourceSampler, "steps", nodeMap, linkMap);
                    if (steps == -1) steps = extractStepsFromWidgets(sourceSampler.get("widgets_values"));
                    if (steps <= 0) steps = 20;

                    if (steps > maxSteps || bestSampler == null) {
                        maxSteps = steps;
                        bestSampler = sourceSampler;
                    }
                }
            }
        }

        if (bestSampler == null) {
            for (JsonNode node : nodes) {
                if (!isNodeActive(node)) continue;
                String type = getNodeType(node).toLowerCase();
                boolean isSampler = type.contains("sampler") || type.contains("clownshark");
                boolean isExcluded = type.contains("detailer") || type.contains("upscale") || type.contains("refiner");

                if (isSampler && !isExcluded) {
                    long steps = resolveNumericParam(node, "steps", nodeMap, linkMap);
                    if (steps == -1) steps = extractStepsFromWidgets(node.get("widgets_values"));
                    if (steps > maxSteps) {
                        maxSteps = steps;
                        bestSampler = node;
                    }
                }
            }
        }

        for (JsonNode node : nodes) {
            if (!isNodeActive(node)) continue;
            String type = getNodeType(node).toLowerCase();

            if (type.contains("guidance") || type.contains("guider")) {
                double g = resolveFloatParam(node, "guidance", nodeMap, linkMap);
                if (g > -1) fluxGuidance = g;
            }

            JsonNode widgets = node.get("widgets_values");
            if (isAllowedModelNode(type)) {
                if (type.contains("loraloader")) extractLoras(widgets, results);
                else extractModelFromList(widgets, results);
            }

            if (type.contains("power lora loader") && node.has("inputs")) {
                extractPowerLoras(node.get("inputs"), results);
            }
        }

        if (bestSampler != null) {
            results.put("_active_sampler_id", bestSampler.get("id").asText());
            if (maxSteps > -1) results.put("Steps", String.valueOf(maxSteps));

            long seed = resolveNumericParam(bestSampler, "seed", nodeMap, linkMap);
            if (seed == -1) seed = resolveNumericParam(bestSampler, "noise_seed", nodeMap, linkMap);
            if (seed == -1) seed = extractSeedFromWidgets(bestSampler.get("widgets_values"));
            if (seed > -1) results.put("Seed", String.valueOf(seed));

            double cfg = resolveFloatParam(bestSampler, "cfg", nodeMap, linkMap);
            if (cfg == -1 || Math.abs(cfg - maxSteps) < 0.001 || cfg < 1.0) {
                cfg = extractCfgFromWidgets(bestSampler.get("widgets_values"), maxSteps);
            }

            if (cfg > -1) {
                String cfgStr = formatDecimal(cfg);
                if (fluxGuidance > -1) cfgStr += " (distilled " + formatDecimal(fluxGuidance) + ")";
                results.put("CFG", cfgStr);
            }

            String sampler = resolveStringParam(bestSampler, "sampler", nodeMap, linkMap);
            if (sampler == null) sampler = extractKeyword(bestSampler.get("widgets_values"), SAMPLER_KEYWORDS);

            String scheduler = resolveStringParam(bestSampler, "scheduler", nodeMap, linkMap);
            if (scheduler == null) scheduler = extractKeyword(bestSampler.get("widgets_values"), SCHEDULER_KEYWORDS);

            if (sampler != null) results.put("Sampler", sampler);
            if (scheduler != null) results.put("Scheduler", scheduler);

            extractPromptsGraph(bestSampler, nodeMap, linkMap, results);
        } else {
            for (JsonNode node : nodes) {
                if (isNodeActive(node) && isTextOrPrimitiveNode(node)) {
                    extractPromptText(node, node.get("widgets_values"), results, "Prompt");
                }
            }
        }
    }

    private JsonNode traceBackToSampler(JsonNode node, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap, int depth) {
        if (depth > 50) return null;
        String type = getNodeType(node).toLowerCase();

        if ((type.contains("sampler") || type.contains("clownshark")) && !type.contains("detailer") && !type.contains("upscale")) {
            return node;
        }

        if (node.has("inputs")) {
            for (JsonNode input : node.get("inputs")) {
                String name = input.get("name").asText().toLowerCase();
                boolean followsImage = name.contains("image") || name.contains("latent") || name.contains("pixels") || name.contains("samples") || name.contains("vae");
                boolean isReroute = type.contains("reroute") || type.contains("node") || type.contains("pipe");

                if ((followsImage || isReroute) && input.has("link") && !input.get("link").isNull()) {
                    int linkId = input.get("link").asInt();
                    if (linkMap.containsKey(linkId)) {
                        JsonNode source = nodeMap.get(linkMap.get(linkId));
                        if (source != null && isNodeActive(source)) {
                            JsonNode found = traceBackToSampler(source, nodeMap, linkMap, depth + 1);
                            if (found != null) return found;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void extractPromptsGraph(JsonNode sampler, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap, Map<String, String> results) {
        String pos = traceInputToText(sampler, "positive", nodeMap, linkMap, 0);
        if (pos != null && !pos.isEmpty()) results.put("Prompt", pos);

        String neg = traceInputToText(sampler, "negative", nodeMap, linkMap, 0);
        if (neg != null && !neg.isEmpty()) results.put("Negative Prompt", neg);
    }

    private String traceInputToText(JsonNode node, String inputNameMatch, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap, int depth) {
        if (depth > 50 || !node.has("inputs")) return null;

        for (JsonNode input : node.get("inputs")) {
            String name = input.get("name").asText().toLowerCase();
            if (name.contains(inputNameMatch) && input.has("link")) {
                JsonNode linkNode = input.get("link");
                if (linkNode.isNull()) continue;

                int linkId = linkNode.asInt();
                if (!linkMap.containsKey(linkId)) continue;

                JsonNode source = nodeMap.get(linkMap.get(linkId));
                if (source == null || !isNodeActive(source)) continue;

                String type = getNodeType(source);
                if (type.contains("cliptextencode") || type.contains("prompt"))
                    return resolveNodeText(source, nodeMap, linkMap, 0);

                if (PASSTHROUGH_TYPES.stream().anyMatch(type::contains) || type.contains("combine") || type.contains("average")
                        || type.contains("cond") || type.contains("concat") || type.contains("jps") || type.contains("text")) {

                    String res = traceInputToText(source, "conditioning", nodeMap, linkMap, depth + 1);
                    if (res == null) res = traceInputToText(source, "average", nodeMap, linkMap, depth + 1);
                    if (res == null) res = traceInputToText(source, "string", nodeMap, linkMap, depth + 1);
                    if (res == null) res = traceInputToText(source, "text", nodeMap, linkMap, depth + 1);
                    if (res == null) res = traceInputToText(source, "", nodeMap, linkMap, depth + 1);
                    if (res != null) return res;

                    String selfText = resolveNodeText(source, nodeMap, linkMap, 0);
                    if (selfText != null && !selfText.isEmpty()) return selfText;
                }
            }
        }
        return null;
    }

    private String resolveNodeText(JsonNode node, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap, int depth) {
        if (depth > 50) return null;
        String type = getNodeType(node).toLowerCase();

        if (type.contains("concat") || type.contains("jps")) return resolveStringSource(node, nodeMap, linkMap, depth);

        if (node.has("inputs")) {
            for (JsonNode input : node.get("inputs")) {
                String name = input.get("name").asText().toLowerCase();
                boolean isPassThrough = PASSTHROUGH_TYPES.stream().anyMatch(type::contains);
                boolean isValidInput = name.contains("text") || name.contains("string") || name.contains("prompt") ||
                        name.equals("value") || name.equals("input") || name.equals("question");

                if (input.has("link") && !input.get("link").isNull() && (isValidInput || (isPassThrough && (name.isEmpty() || name.equals("*"))))) {
                    int linkId = input.get("link").asInt();
                    if (linkMap.containsKey(linkId)) {
                        JsonNode source = nodeMap.get(linkMap.get(linkId));
                        if (source != null && isNodeActive(source)) {
                            String resolved = resolveStringSource(source, nodeMap, linkMap, depth + 1);
                            if (resolved != null && !resolved.isEmpty()) return resolved;
                        }
                    }
                }
            }
        }

        if (node.has("widgets_values")) {
            String bestText = null;
            for (JsonNode w : node.get("widgets_values")) {
                if (w.isTextual()) {
                    String val = w.asText().trim();
                    if (isValidPrompt(val)) {
                        if (bestText == null || val.length() > bestText.length()) bestText = val;
                    }
                }
            }
            return bestText;
        }
        return null;
    }

    private String resolveStringSource(JsonNode node, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap, int depth) {
        String type = getNodeType(node).toLowerCase();

        if (type.contains("concat") || type.contains("jps") || type.contains("combin")) {
            StringBuilder sb = new StringBuilder();
            Map<String, JsonNode> relevantInputs = new TreeMap<>();

            if (node.has("inputs")) {
                for (JsonNode input : node.get("inputs")) {
                    String name = input.get("name").asText().toLowerCase();
                    if (name.contains("text") || name.contains("string") || name.contains("input")) {
                        relevantInputs.put(input.get("name").asText(), input);
                    }
                }
            }

            for (Map.Entry<String, JsonNode> entry : relevantInputs.entrySet()) {
                JsonNode input = entry.getValue();
                String resolvedPart = null;

                if (input.has("link") && !input.get("link").isNull()) {
                    int linkId = input.get("link").asInt();
                    if (linkMap.containsKey(linkId)) {
                        JsonNode linkedNode = nodeMap.get(linkMap.get(linkId));
                        if (linkedNode != null)
                            resolvedPart = resolveStringSource(linkedNode, nodeMap, linkMap, depth + 1);
                    }
                }

                if (resolvedPart != null && !resolvedPart.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(resolvedPart);
                }
            }

            if (sb.isEmpty() && node.has("widgets_values")) {
                for (JsonNode w : node.get("widgets_values")) {
                    if (w.isTextual() && isValidPrompt(w.asText())) sb.append(w.asText()).append(" ");
                }
            }
            return sb.toString().trim();
        }

        if (PASSTHROUGH_TYPES.stream().anyMatch(type::contains) || type.contains("string") || type.contains("text")) {
            return resolveNodeText(node, nodeMap, linkMap, depth);
        }
        return resolveNodeText(node, nodeMap, linkMap, depth);
    }

    private void processGlobalSeedMap(JsonNode seedWidgets, JsonNode nodes, Map<String, String> results) {
        String activeSamplerId = results.get("_active_sampler_id");
        if (activeSamplerId == null) return;
        Iterator<Map.Entry<String, JsonNode>> fields = seedWidgets.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!entry.getKey().equals(activeSamplerId)) continue;
            int widgetIndex = entry.getValue().asInt();
            for (JsonNode node : nodes) {
                if (String.valueOf(node.get("id").asInt()).equals(entry.getKey())) {
                    JsonNode widgets = node.get("widgets_values");
                    if (widgets != null && widgets.size() > widgetIndex) {
                        JsonNode val = widgets.get(widgetIndex);
                        if (isNumeric(val)) results.put("Seed", val.asText());
                    }
                }
            }
        }
        results.remove("_active_sampler_id");
    }

    private void processInputsBlock(JsonNode inputs, JsonNode node, Map<String, String> results, boolean skipCoreParams) {
        String type = getNodeType(node).toLowerCase();
        String title = getNodeTitle(node).toLowerCase();
        boolean lockCore = results.containsKey("_lock_core_params");

        Iterator<Map.Entry<String, JsonNode>> fields = inputs.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String k = field.getKey().toLowerCase();
            JsonNode v = field.getValue();

            if (!skipCoreParams && !lockCore) {
                if (k.equals("scheduler")) results.put("Scheduler", v.asText());
                if (k.equals("sampler_name")) results.put("Sampler", v.asText());
            }

            if (isNumeric(v)) {
                if (!skipCoreParams && !lockCore) {
                    if (k.equals("steps")) results.put("Steps", v.asText());
                    if (k.equals("cfg") || k.equals("cfg_scale")) results.put("CFG", v.asText());
                    if ((k.equals("seed") || k.equals("noise_seed")) && !results.containsKey("_seed_locked"))
                        results.put("Seed", v.asText());
                }
            } else if (v.isTextual()) {
                String txt = v.asText();
                if (isAllowedModelNode(type) && isValidModelFile(txt) && (k.contains("ckpt") || k.contains("model") || k.contains("unet") || k.contains("file"))) {
                    results.put("Model", cleanFilename(txt));
                }
                if (type.contains("lora") && k.contains("lora") && isValidModelFile(txt)) {
                    double strength = 1.0;
                    if (inputs.has("strength_model")) strength = inputs.get("strength_model").asDouble();
                    else if (inputs.has("strength")) strength = inputs.get("strength").asDouble();
                    appendResult(results, "Loras", formatLoraString(cleanFilename(txt), strength));
                }
                if (isValidPrompt(txt) && (k.contains("prompt") || k.contains("text"))) {
                    String targetKey = (title.contains("negative") || isNegativeNode(node)) ? "Negative" : "Prompt";
                    appendResult(results, targetKey, txt);
                    extractLorasFromPrompt(txt, results);
                }
            }
        }

        // Custom Node Handling
        if (userDataManager != null) {
            List<String> customPromptNodes = userDataManager.getCustomPromptNodes();
            List<String> customLoraNodes = userDataManager.getCustomLoraNodes();

            if (customPromptNodes != null && customPromptNodes.stream().anyMatch(type::contains)) {
                if (inputs.has("value") && inputs.get("value").isTextual()) {
                    String txt = inputs.get("value").asText();
                    String targetKey = (title.contains("negative") || isNegativeNode(node)) ? "Negative" : "Prompt";
                    appendResult(results, targetKey, txt);
                }
            }

            if (customLoraNodes != null && customLoraNodes.stream().anyMatch(type::contains)) {
                if (inputs.has("lora_stack") && inputs.get("lora_stack").isTextual()) {
                    String stack = inputs.get("lora_stack").asText();
                    String[] loras = stack.split(",");
                    for (String lora : loras) {
                        String clean = lora.trim();
                        if (!clean.isEmpty()) {
                            appendResult(results, "Loras", formatLoraString(clean, 1.0));
                        }
                    }
                }
            }
        }
    }

    private boolean isNodeActive(JsonNode node) {
        if (!node.has("mode")) return true;
        if (node.get("mode").isNumber()) {
            int m = node.get("mode").asInt();
            return m != 2 && m != 4;
        }
        return true;
    }

    private Map<Integer, Integer> buildLinkMap(JsonNode links) {
        Map<Integer, Integer> map = new HashMap<>();
        if (links != null)
            for (JsonNode link : links) if (link.size() >= 2) map.put(link.get(0).asInt(), link.get(1).asInt());
        return map;
    }

    private Map<Integer, JsonNode> buildNodeMap(JsonNode nodes) {
        Map<Integer, JsonNode> map = new HashMap<>();
        for (JsonNode node : nodes) map.put(node.get("id").asInt(), node);
        return map;
    }

    private boolean isNumeric(JsonNode node) {
        if (node == null || node.isBoolean()) return false;
        return node.isNumber() || (node.isTextual() && node.asText().matches("-?\\d+(\\.\\d+)?"));
    }

    private boolean isInteger(JsonNode node) {
        if (!isNumeric(node)) return false;
        String t = node.asText();
        return !t.contains(".") || t.endsWith(".0");
    }

    private long asLongSafe(JsonNode node) {
        try {
            return node.isNumber() ? node.asLong() : Long.parseLong(node.asText().split("\\.")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean isValidModelFile(String filename) {
        if (filename == null || filename.length() < 3) return false;
        String lower = filename.toLowerCase();
        if (VALID_EXTENSIONS.stream().noneMatch(lower::endsWith)) return false;
        if (IGNORED_FILENAME_PATTERNS.stream().anyMatch(lower::contains)) return false;
        return !lower.equals("true") && !lower.equals("false") && !lower.equals("none");
    }

    private String cleanFilename(String path) {
        if (path.contains("\\")) path = path.substring(path.lastIndexOf("\\") + 1);
        if (path.contains("/")) path = path.substring(path.lastIndexOf("/") + 1);
        return path.replaceAll("\\.(safetensors|gguf|ckpt|pt|pth|bin)$", "");
    }

    private boolean isValidPrompt(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        if (isValidModelFile(text)) return false;
        if (text.startsWith("comma") || text.startsWith("newline")) return false;
        if (IGNORED_PROMPT_TEXTS.stream().anyMatch(text::equalsIgnoreCase)) return false;
        if (text.contains("Select Wildcard") && text.contains("Full Cache")) return false;
        return !text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false");
    }

    private String getNodeType(JsonNode node) {
        if (node.has("class_type")) return node.get("class_type").asText().toLowerCase();
        if (node.has("type")) return node.get("type").asText().toLowerCase();
        return "";
    }

    private String getNodeTitle(JsonNode node) {
        if (node.has("title")) return node.get("title").asText().toLowerCase();
        if (node.has("_meta") && node.get("_meta").has("title"))
            return node.get("_meta").get("title").asText().toLowerCase();
        return "";
    }

    private boolean isTextOrPrimitiveNode(JsonNode node) {
        String type = getNodeType(node);
        if (type.contains("janus")) return false;
        return type.contains("text") || type.contains("prompt") || type.contains("primitive") || type.contains("string") ||
                type.contains("portrait") || type.contains("processor") || type.contains("wildcard") ||
                type.contains("manager");
    }

    private boolean isNegativeNode(JsonNode node) {
        String title = getNodeTitle(node).toLowerCase();
        if (title.contains("negative")) return true;
        String type = getNodeType(node);
        return type.contains("negative") || type.contains("neg ");
    }

    private void appendResult(Map<String, String> results, String key, String newText) {
        String existing = results.get(key);
        if (existing == null || existing.isEmpty()) results.put(key, newText);
        else if (!existing.contains(newText)) results.put(key, existing + ", " + newText);
    }

    private long resolveNumericParam(JsonNode node, String paramName, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap) {
        if (!node.has("inputs")) return -1;
        for (JsonNode input : node.get("inputs")) {
            String name = input.get("name").asText().toLowerCase();
            if (name.contains(paramName)) {
                if (input.has("link") && !input.get("link").isNull() && input.get("link").isInt()) {
                    int linkId = input.get("link").asInt();
                    if (linkMap.containsKey(linkId)) {
                        JsonNode source = nodeMap.get(linkMap.get(linkId));
                        if (source != null && isNodeActive(source)) {
                            long val = extractFirstNumeric(source.get("widgets_values"));
                            if (val > -1) return val;
                        }
                    }
                }
                if (input.has("widget") && input.get("widget").has("value")) {
                    JsonNode val = input.get("widget").get("value");
                    if (isNumeric(val)) return val.asLong();
                }
            }
        }
        return -1;
    }

    private double resolveFloatParam(JsonNode node, String paramName, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap) {
        if (node.has("inputs")) {
            for (JsonNode input : node.get("inputs")) {
                String name = input.get("name").asText().toLowerCase();
                if (name.contains(paramName)) {
                    if (input.has("link") && !input.get("link").isNull() && input.get("link").isInt()) {
                        int linkId = input.get("link").asInt();
                        if (linkMap.containsKey(linkId)) {
                            JsonNode source = nodeMap.get(linkMap.get(linkId));
                            if (source != null && isNodeActive(source)) {
                                if (source.has("widgets_values")) {
                                    for (JsonNode w : source.get("widgets_values"))
                                        if (isNumeric(w)) return w.asDouble();
                                }
                                if (source.has("inputs") && source.get("inputs").has(paramName)) {
                                    JsonNode v = source.get("inputs").get(paramName);
                                    if (isNumeric(v)) return v.asDouble();
                                }
                            }
                        }
                    }
                    if (input.has("widget") && input.get("widget").has("value")) {
                        JsonNode val = input.get("widget").get("value");
                        if (isNumeric(val)) return val.asDouble();
                    }
                }
            }
        }
        if (node.has("widgets_values")) {
            for (JsonNode w : node.get("widgets_values")) {
                if (isNumeric(w) && !w.isBoolean()) {
                    if (paramName.equals("guidance")) return w.asDouble();
                    if (paramName.contains("cfg") && !w.isIntegralNumber()) {
                        double v = w.asDouble();
                        if (v >= 1.0 && v <= 100.0) return v;
                    }
                }
            }
        }
        return -1;
    }

    private String resolveStringParam(JsonNode node, String paramName, Map<Integer, JsonNode> nodeMap, Map<Integer, Integer> linkMap) {
        if (!node.has("inputs")) return null;
        for (JsonNode input : node.get("inputs")) {
            String name = input.get("name").asText().toLowerCase();
            if (name.contains(paramName)) {
                if (input.has("widget") && input.get("widget").has("value"))
                    return input.get("widget").get("value").asText();
                if (input.has("link") && !input.get("link").isNull() && input.get("link").isInt()) {
                    int linkId = input.get("link").asInt();
                    if (linkMap.containsKey(linkId)) {
                        JsonNode source = nodeMap.get(linkMap.get(linkId));
                        if (source != null && isNodeActive(source)) {
                            if (source.has("widgets_values")) {
                                JsonNode w = source.get("widgets_values").get(0);
                                if (w != null && w.isTextual()) return w.asText();
                            }
                            if (source.has("inputs")) {
                                if (source.get("inputs").has("sampler_name"))
                                    return source.get("inputs").get("sampler_name").asText();
                                if (source.get("inputs").has("scheduler"))
                                    return source.get("inputs").get("scheduler").asText();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void extractModelFromList(JsonNode widgets, Map<String, String> results) {
        if (widgets == null) return;
        for (JsonNode w : widgets) {
            if (w.isTextual()) {
                String txt = w.asText();
                if (isValidModelFile(txt)) results.put("Model", cleanFilename(txt));
            }
        }
    }

    private long extractStepsFromWidgets(JsonNode widgets) {
        if (widgets == null) return -1;
        for (JsonNode w : widgets) {
            if (w.isBoolean()) continue;
            if (isInteger(w)) {
                long val = w.asLong();
                if (val > 1 && val <= 1000) return val;
            }
        }
        return -1;
    }

    private long extractSeedFromWidgets(JsonNode widgets) {
        if (widgets == null) return -1;
        for (JsonNode w : widgets)
            if (isNumeric(w)) {
                long val = w.asLong();
                if (val > 1000000) return val;
            }
        return -1;
    }

    private double extractCfgFromWidgets(JsonNode widgets, long stepsValue) {
        if (widgets == null) return -1;
        for (JsonNode w : widgets)
            if (isNumeric(w)) {
                double val = w.asDouble();
                if (stepsValue > 0 && Math.abs(val - stepsValue) < 0.001) continue;
                if (val >= 1.0 && val <= 100.0) return val;
            }
        return -1;
    }

    private String extractKeyword(JsonNode widgets, Set<String> keywords) {
        if (widgets == null) return null;
        for (JsonNode w : widgets)
            if (w.isTextual() && keywords.stream().anyMatch(w.asText().toLowerCase()::contains)) return w.asText();
        return null;
    }

    private void extractPromptText(JsonNode node, JsonNode widgets, Map<String, String> results, String targetKey) {
        if (widgets == null) return;
        if (node.has("inputs")) {
            for (JsonNode input : node.get("inputs")) {
                String name = input.get("name").asText().toLowerCase();
                if ((name.equals("text") || name.equals("string") || name.equals("prompt")) && input.has("link") && !input.get("link").isNull())
                    return;
            }
        }
        for (JsonNode w : widgets) {
            if (w.isTextual()) {
                String txt = w.asText().trim();
                if (isValidPrompt(txt)) {
                    appendResult(results, targetKey, txt);
                    extractLorasFromPrompt(txt, results);
                }
            }
        }
    }

    private String formatLoraString(String name, double strength) {
        return "<lora:" + name + ":" + formatDecimal(strength) + ">";
    }

    private void extractPowerLoras(JsonNode inputs, Map<String, String> results) {
        if (inputs == null) return;
        Iterator<Map.Entry<String, JsonNode>> fields = inputs.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (key.startsWith("lora") && val.isObject()) {
                boolean isOn = val.has("on") && val.get("on").asBoolean(false);
                if (isOn && val.has("lora")) {
                    String name = val.get("lora").asText();
                    double strength = val.has("strength") ? val.get("strength").asDouble(1.0) : 1.0;
                    if (isValidModelFile(name)) {
                        name = cleanFilename(name);
                        appendResult(results, "Loras", formatLoraString(name, strength));
                    }
                }
            }
        }
    }

    private void extractLoras(JsonNode widgets, Map<String, String> results) {
        if (widgets == null) return;
        String name = null;
        double strength = 1.0;
        for (JsonNode w : widgets) {
            if (w.isTextual() && isValidModelFile(w.asText())) name = cleanFilename(w.asText());
            else if (isNumeric(w)) strength = w.asDouble();
        }
        if (name != null) {
            String existing = results.getOrDefault("Loras", "");
            if (!existing.contains(name)) appendResult(results, "Loras", formatLoraString(name, strength));
        }
    }

    private void extractLorasFromPrompt(String promptText, Map<String, String> results) {
        if (promptText == null || !promptText.contains("<lora:")) return;
        Matcher m = LORA_TAG_PATTERN.matcher(promptText);
        while (m.find()) {
            String name = cleanFilename(m.group(1));
            String strVal = m.group(2);
            double strength = 1.0;
            if (strVal != null) {
                try {
                    strength = Double.parseDouble(strVal);
                } catch (NumberFormatException e) {
                }
            }
            String existing = results.getOrDefault("Loras", "");
            if (!existing.contains(name)) appendResult(results, "Loras", formatLoraString(name, strength));
        }
    }

    private JsonNode getLinkedNodeApi(JsonNode node, String inputName, JsonNode root) {
        if (!node.has("inputs")) return null;
        JsonNode inputs = node.get("inputs");
        if (inputs.has(inputName)) {
            JsonNode val = inputs.get(inputName);
            if (val.isArray() && val.size() == 2) return root.get(val.get(0).asText());
        }
        return null;
    }

    private long resolveNumericParamRecursive(JsonNode node, String paramName, JsonNode root) {
        if (!node.has("inputs")) return -1;
        JsonNode inputs = node.get("inputs");
        if (inputs.has(paramName)) {
            JsonNode val = inputs.get(paramName);
            if (isNumeric(val)) return asLongSafe(val);
            if (val.isArray() && val.size() == 2) return resolveValueRecursive(root.get(val.get(0).asText()), root);
        }
        return -1;
    }

    private double resolveFloatParamRecursive(JsonNode node, String paramName, JsonNode root) {
        if (!node.has("inputs")) return -1;
        JsonNode inputs = node.get("inputs");
        if (inputs.has(paramName)) {
            JsonNode val = inputs.get(paramName);
            if (isNumeric(val)) return val.asDouble();
            if (val.isArray() && val.size() == 2)
                return resolveFloatValueRecursive(root.get(val.get(0).asText()), root);
        }
        return -1;
    }

    private String resolveStringParamRecursive(JsonNode node, String paramName, JsonNode root) {
        if (!node.has("inputs")) return null;
        JsonNode inputs = node.get("inputs");
        if (inputs.has(paramName)) {
            JsonNode val = inputs.get(paramName);
            if (val.isTextual()) return val.asText();
            if (val.isArray() && val.size() == 2) {
                JsonNode source = root.get(val.get(0).asText());
                if (source != null && source.has("inputs")) {
                    JsonNode srcIn = source.get("inputs");
                    if (srcIn.has("Value")) return srcIn.get("Value").asText();
                    if (srcIn.has("text")) return srcIn.get("text").asText();
                    if (srcIn.has("string")) return srcIn.get("string").asText();
                }
            }
        }
        return null;
    }

    private long resolveValueRecursive(JsonNode node, JsonNode root) {
        if (node == null || !node.has("inputs")) return -1;
        JsonNode inputs = node.get("inputs");
        if (inputs.has("Value")) return asLongSafe(inputs.get("Value"));
        if (inputs.has("value")) return asLongSafe(inputs.get("value"));
        if (inputs.has("seed")) return asLongSafe(inputs.get("seed"));
        return -1;
    }

    private double resolveFloatValueRecursive(JsonNode node, JsonNode root) {
        if (node == null || !node.has("inputs")) return -1;
        JsonNode inputs = node.get("inputs");
        if (inputs.has("Value")) return inputs.get("Value").asDouble();
        if (inputs.has("value")) return inputs.get("value").asDouble();
        return -1;
    }

    private boolean isAllowedModelNode(String type) {
        if (IGNORED_MODEL_NODE_TYPES.stream().anyMatch(type::contains)) return false;
        return ALLOWED_MODEL_NODE_TYPES.stream().anyMatch(type::contains);
    }

    private long extractFirstNumeric(JsonNode widgets) {
        if (widgets == null) return -1;
        for (JsonNode w : widgets) if (isNumeric(w)) return asLongSafe(w);
        return -1;
    }

    private String formatDecimal(double value) {
        return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US)).format(value);
    }
}
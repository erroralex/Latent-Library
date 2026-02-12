package com.nilsson.backend.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StrategyTests is a comprehensive test suite designed to verify the accuracy and reliability of the various
 * metadata extraction strategies employed by the application. It ensures that technical generation parameters
 * from diverse AI software platforms—including NovelAI, SwarmUI, and InvokeAI—are correctly decoded from
 * their native JSON structures and mapped to the application's standardized metadata schema. The tests
 * validate the extraction of critical fields such as prompts, models, seeds, CFG scales, and samplers,
 * ensuring consistent data representation across the system.
 */
class StrategyTests {

    @Test
    @DisplayName("NovelAIStrategy should parse NovelAI JSON metadata")
    void testNovelAIStrategy() {
        NovelAIStrategy strategy = new NovelAIStrategy();
        String json = """
                {
                    "prompt": "masterpiece, best quality, 1girl, aqua eyes",
                    "uc": "lowres, bad anatomy",
                    "scale": 11.0,
                    "steps": 28,
                    "seed": 123456789,
                    "sampler": "k_euler_ancestral",
                    "software": "novelai"
                }
                """;

        Map<String, String> results = strategy.parse(json);

        assertEquals("masterpiece, best quality, 1girl, aqua eyes", results.get("Prompt"));
        assertEquals("lowres, bad anatomy", results.get("Negative"));
        assertEquals("11.0", results.get("CFG"));
        assertEquals("28", results.get("Steps"));
        assertEquals("123456789", results.get("Seed"));
        assertEquals("k_euler_ancestral", results.get("Sampler"));
        assertEquals("NovelAI Diffusion", results.get("Model"));
    }

    @Test
    @DisplayName("SwarmUIStrategy should parse SwarmUI JSON metadata")
    void testSwarmUIStrategy() {
        SwarmUIStrategy strategy = new SwarmUIStrategy();
        String json = """
                {
                    "prompt": "A futuristic city at night",
                    "negativeprompt": "blurry, distorted",
                    "model": "flux_v1.safetensors",
                    "cfgscale": 7.5,
                    "steps": 30,
                    "seed": 987654321,
                    "sampler": "dpmpp_2m_sde"
                }
                """;

        Map<String, String> results = strategy.parse(json);

        assertEquals("A futuristic city at night", results.get("Prompt"));
        assertEquals("blurry, distorted", results.get("Negative"));
        assertEquals("flux_v1.safetensors", results.get("Model"));
        assertEquals("7.5", results.get("CFG"));
        assertEquals("30", results.get("Steps"));
        assertEquals("987654321", results.get("Seed"));
        assertEquals("dpmpp_2m_sde", results.get("Sampler"));
    }

    @Test
    @DisplayName("InvokeAIStrategy should parse InvokeAI JSON metadata")
    void testInvokeAIStrategy() {
        InvokeAIStrategy strategy = new InvokeAIStrategy();
        String json = """
                {
                    "positive_prompt": "Portrait of a wizard",
                    "negative_prompt": "ugly, deformed",
                    "model_name": "sdxl_base",
                    "cfg_scale": 8.0,
                    "sampler_name": "euler_a"
                }
                """;

        Map<String, String> results = strategy.parse(json);

        assertEquals("Portrait of a wizard", results.get("Prompt"));
        assertEquals("ugly, deformed", results.get("Negative"));
        assertEquals("sdxl_base", results.get("Model"));
        assertEquals("8.0", results.get("CFG"));
        assertEquals("euler_a", results.get("Sampler"));
    }
}
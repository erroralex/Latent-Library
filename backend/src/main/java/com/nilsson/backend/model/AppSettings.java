package com.nilsson.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data model representing the persistent application settings stored in {@code settings.json}.
 * <p>
 * This class serves as the primary configuration holder for user preferences and application state
 * that must persist across sessions. It is designed to be serialized and deserialized using Jackson.
 * It includes settings for the user interface theme, navigation history, directory exclusion rules,
 * and tool-specific configurations like the Speed Sorter.
 * <p>
 * Key Components:
 * <ul>
 *   <li><b>Navigation State:</b> Tracks the {@code lastFolder} visited by the user to provide a seamless
 *   resume experience.</li>
 *   <li><b>UI Customization:</b> Stores the active {@code theme} name (defaulting to "neon").</li>
 *   <li><b>Indexer Configuration:</b> Maintains a list of {@code excludedPaths} that the background
 *   indexing service should ignore.</li>
 *   <li><b>Speed Sorter Settings:</b> Encapsulates the source directory and the five target slots
 *   used by the rapid triage utility.</li>
 * </ul>
 */
public class AppSettings {
    private String lastFolder;
    private String theme = "neon";
    private List<String> excludedPaths = new ArrayList<>();
    private SpeedSorterSettings speedSorter = new SpeedSorterSettings();
    private List<String> customPromptNodes = new ArrayList<>();
    private List<String> customLoraNodes = new ArrayList<>();

    public String getLastFolder() {
        return lastFolder;
    }

    public void setLastFolder(String lastFolder) {
        this.lastFolder = lastFolder;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public SpeedSorterSettings getSpeedSorter() {
        return speedSorter;
    }

    public void setSpeedSorter(SpeedSorterSettings speedSorter) {
        this.speedSorter = speedSorter;
    }

    public List<String> getCustomPromptNodes() {
        return customPromptNodes;
    }

    public void setCustomPromptNodes(List<String> customPromptNodes) {
        this.customPromptNodes = customPromptNodes;
    }

    public List<String> getCustomLoraNodes() {
        return customLoraNodes;
    }

    public void setCustomLoraNodes(List<String> customLoraNodes) {
        this.customLoraNodes = customLoraNodes;
    }

    /**
     * Configuration settings specific to the Speed Sorter utility.
     */
    public static class SpeedSorterSettings {
        private String inputDir;
        private List<String> targets = new ArrayList<>(Collections.nCopies(5, null));

        public String getInputDir() {
            return inputDir;
        }

        public void setInputDir(String inputDir) {
            this.inputDir = inputDir;
        }

        public List<String> getTargets() {
            return targets;
        }

        public void setTargets(List<String> targets) {
            this.targets = (targets != null) ? new ArrayList<>(targets) : new ArrayList<>();

            while (this.targets.size() < 5) {
                this.targets.add(null);
            }
        }
    }
}

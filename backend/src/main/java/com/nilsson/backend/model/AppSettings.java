package com.nilsson.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the structure of the persistent settings.json file.
 */
public class AppSettings {
    private String lastFolder;
    private List<String> excludedPaths = new ArrayList<>();
    private SpeedSorterSettings speedSorter = new SpeedSorterSettings();

    public String getLastFolder() {
        return lastFolder;
    }

    public void setLastFolder(String lastFolder) {
        this.lastFolder = lastFolder;
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

    public static class SpeedSorterSettings {
        private String inputDir;
        // Initialize with 5 nulls for the 5 slots
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
            // Wrap in a mutable ArrayList to allow resizing/modification
            this.targets = (targets != null) ? new ArrayList<>(targets) : new ArrayList<>();

            // Ensure size is always 5
            while (this.targets.size() < 5) {
                this.targets.add(null);
            }
        }
    }
}

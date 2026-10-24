package com.tracex.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Shortest criminal link pathway between two investigation entities.
 * Structured into individual entity hops and connecting relationship bridges.
 */
public class PathResult {

    private final boolean found;
    private final String message;
    private final List<PathStep> steps;
    private final List<String> lines;
    private final int entityCount;
    private final int linkCount;

    /**
     * An individual entity node hop along the chain.
     */
    public static class PathStep {
        private final int index;
        private final String key;
        private final String label;
        private final String type;
        private final String role;
        private final String linkToNext;
        private final String linkRawKind;
        private final String linkEvidence;
        private final boolean last;

        public PathStep(int index, String key, String label, String type, String role,
                        String linkToNext, String linkRawKind, String linkEvidence, boolean last) {
            this.index = index;
            this.key = key;
            this.label = label;
            this.type = type;
            this.role = role;
            this.linkToNext = linkToNext;
            this.linkRawKind = linkRawKind;
            this.linkEvidence = linkEvidence;
            this.last = last;
        }

        public int getIndex() {
            return index;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public String getRole() {
            return role;
        }

        public String getLinkToNext() {
            return linkToNext;
        }

        public String getLinkRawKind() {
            return linkRawKind;
        }

        public String getLinkEvidence() {
            return linkEvidence;
        }

        public boolean isLast() {
            return last;
        }
    }

    private PathResult(boolean found, String message, List<PathStep> steps, List<String> lines) {
        this.found = found;
        this.message = message;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.lines = lines != null ? lines : new ArrayList<>();
        this.entityCount = this.steps.size();
        this.linkCount = Math.max(0, this.steps.size() - 1);
    }

    public static PathResult found(List<PathStep> steps, List<String> lines) {
        return new PathResult(true, "", steps, lines);
    }

    public static PathResult found(List<String> lines) {
        return new PathResult(true, "", new ArrayList<>(), lines);
    }

    public static PathResult notFound(String message) {
        return new PathResult(false, message, new ArrayList<>(), new ArrayList<>());
    }

    public boolean isFound() {
        return found;
    }

    public String getMessage() {
        return message;
    }

    public List<PathStep> getSteps() {
        return steps;
    }

    public List<String> getLines() {
        return lines;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public int getLinkCount() {
        return linkCount;
    }
}

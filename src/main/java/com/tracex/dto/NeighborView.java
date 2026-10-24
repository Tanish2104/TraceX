package com.tracex.dto;

import com.tracex.util.RelationshipFormatter;

/** A directly linked entity, shown on the entity detail page. */
public class NeighborView {

    private final String key;
    private final String label;
    private final String type;
    private final String kind;
    private final double weight;
    private final String evidence;

    public NeighborView(String key, String label, String type, String kind, double weight, String evidence) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.kind = kind;
        this.weight = weight;
        this.evidence = evidence;
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

    public String getKind() {
        return kind;
    }

    public String getFormattedKind() {
        return RelationshipFormatter.formatKind(kind);
    }

    public double getWeight() {
        return weight;
    }

    public String getEvidence() {
        return evidence;
    }
}

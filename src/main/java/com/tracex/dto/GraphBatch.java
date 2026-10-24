package com.tracex.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the nodes and edges produced by one upload so they can be written to Neo4j in bulk.
 * Repeated edges between the same two nodes with the same kind are summed.
 */
public class GraphBatch {

    private final Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> edges = new LinkedHashMap<>();

    public void addNode(String key, String type, String label) {
        Map<String, Object> existing = nodes.get(key);
        if (existing == null) {
            Map<String, Object> node = new HashMap<>();
            node.put("key", key);
            node.put("type", type);
            node.put("label", label);
            nodes.put(key, node);
        } else if (label.length() > ((String) existing.get("label")).length()) {
            existing.put("label", label);
        }
    }

    public void addEdge(String srcKey, String dstKey, String kind, double weight, String evidence) {
        if (srcKey.equals(dstKey)) {
            return;
        }
        String id = srcKey + "|" + dstKey + "|" + kind;
        Map<String, Object> existing = edges.get(id);
        if (existing == null) {
            Map<String, Object> edge = new HashMap<>();
            edge.put("srcKey", srcKey);
            edge.put("dstKey", dstKey);
            edge.put("kind", kind);
            edge.put("weight", weight);
            edge.put("evidence", evidence);
            edges.put(id, edge);
        } else {
            double total = ((Double) existing.get("weight")) + weight;
            existing.put("weight", total);
        }
    }

    public List<Map<String, Object>> nodeRows() {
        return new ArrayList<>(nodes.values());
    }

    public List<Map<String, Object>> edgeRows() {
        return new ArrayList<>(edges.values());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }
}

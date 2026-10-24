package com.tracex.dto;

import java.util.List;

/** JSON shape returned by /api/graph and drawn by vis-network. */
public record GraphData(List<Node> nodes, List<Edge> edges) {

    public record Node(String id, String label, String type, double score, long community) {
    }

    public record Edge(String from, String to, String kind, double weight, String evidence) {
    }
}

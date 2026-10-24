package com.tracex.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClosenessCentrality;
import org.jgrapht.alg.scoring.EigenvectorCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.springframework.stereotype.Service;

import com.tracex.repository.Neo4jGateway;
import com.tracex.util.EntityTypes;

/**
 * Stage 4: loads the graph into JGraphT, computes centrality scores and communities, builds a plain-language
 * evidence sentence for every entity, and writes everything back onto the Neo4j nodes.
 * The analysis is deterministic: running it twice on the same graph gives the same ranking.
 */
@Service
public class AnalysisService {

    private static final int CHUNK = 500;
    private static final double W_DEGREE = 0.30;
    private static final double W_BETWEENNESS = 0.30;
    private static final double W_CLOSENESS = 0.20;
    private static final double W_EIGENVECTOR = 0.20;

    /** What a run produced, used for the confirmation message. */
    public record Summary(int entities, int links, int communities, String eigenvectorMethod) {
    }

    private record Neighbor(String key, String kind, double weight) {
    }

    private final Neo4jGateway db;
    private final AuditService audit;

    public AnalysisService(Neo4jGateway db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    public Summary run(String username) {
        List<Map<String, Object>> nodeRows = db.read(
                "MATCH (e:Entity) RETURN e.key AS key, e.label AS label, e.type AS type", Map.of());
        if (nodeRows.isEmpty()) {
            throw new IllegalStateException("The graph is empty. Upload data first.");
        }
        List<Map<String, Object>> edgeRows = db.read(
                "MATCH (a:Entity)-[r:LINKED]->(b:Entity) RETURN a.key AS src, b.key AS dst, r.kind AS kind, "
                        + "coalesce(r.weight, 1.0) AS weight", Map.of());

        // ---- build the JGraphT graph (undirected, one edge per pair of entities) ----
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> row : nodeRows) {
            // Places are drawn on the graph but not scored: two people living in one district is not a real link,
            // and a district node would otherwise act as a false hub between unrelated people.
            if (EntityTypes.LOCATION.equals(String.valueOf(row.get("type")))) {
                continue;
            }
            String key = String.valueOf(row.get("key"));
            graph.addVertex(key);
            names.put(key, String.valueOf(row.get("type")).toLowerCase() + " " + row.get("label"));
        }
        Map<String, List<Neighbor>> adjacency = new HashMap<>();
        for (Map<String, Object> row : edgeRows) {
            String src = String.valueOf(row.get("src"));
            String dst = String.valueOf(row.get("dst"));
            String kind = String.valueOf(row.get("kind"));
            double weight = ((Number) row.get("weight")).doubleValue();
            if (src.equals(dst) || !graph.containsVertex(src) || !graph.containsVertex(dst)) {
                continue;
            }
            if (!graph.containsEdge(src, dst)) {
                graph.addEdge(src, dst);
            }
            adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(new Neighbor(dst, kind, weight));
            adjacency.computeIfAbsent(dst, k -> new ArrayList<>()).add(new Neighbor(src, kind, weight));
        }

        // ---- centrality ----
        Map<String, Double> degree = new HashMap<>();
        for (String v : graph.vertexSet()) {
            degree.put(v, (double) graph.degreeOf(v));
        }
        Map<String, Double> betweenness = new BetweennessCentrality<>(graph, true).getScores();
        Map<String, Double> closeness = new ClosenessCentrality<>(graph).getScores();

        Map<String, Double> eigenvector;
        String method = "eigenvector centrality";
        try {
            eigenvector = new EigenvectorCentrality<String, DefaultEdge>(graph).getScores();
        } catch (RuntimeException ex) {
            // Power iteration can fail to converge (for example on bipartite graphs). PageRank always converges.
            eigenvector = new PageRank<String, DefaultEdge>(graph).getScores();
            method = "PageRank (eigenvector centrality did not converge)";
        }

        Map<String, Double> degreeN = normalize(degree);
        Map<String, Double> betweennessN = normalize(betweenness);
        Map<String, Double> closenessN = normalize(closeness);
        Map<String, Double> eigenvectorN = normalize(eigenvector);

        // ---- communities ----
        Map<String, Integer> community = detectCommunities(graph);
        int communityCount = new TreeSet<>(community.values()).size();

        // ---- one row per entity, with evidence ----
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String v : graph.vertexSet()) {
            double score = 100.0 * (W_DEGREE * degreeN.getOrDefault(v, 0.0)
                    + W_BETWEENNESS * betweennessN.getOrDefault(v, 0.0)
                    + W_CLOSENESS * closenessN.getOrDefault(v, 0.0)
                    + W_EIGENVECTOR * eigenvectorN.getOrDefault(v, 0.0));
            Map<String, Object> out = new HashMap<>();
            out.put("key", v);
            out.put("degree", (long) graph.degreeOf(v));
            out.put("betweenness", round(betweenness.getOrDefault(v, 0.0), 4));
            out.put("closeness", round(closeness.getOrDefault(v, 0.0), 4));
            out.put("eigenvector", round(eigenvector.getOrDefault(v, 0.0), 4));
            out.put("score", round(score, 2));
            out.put("community", (long) community.get(v));
            out.put("evidence", evidence(v, graph, adjacency, names, community));
            rows.add(out);
        }

        String writeBack = "UNWIND $rows AS row MATCH (e:Entity {key: row.key}) "
                + "SET e.degree = row.degree, e.betweenness = row.betweenness, e.closeness = row.closeness, "
                + "e.eigenvector = row.eigenvector, e.score = row.score, e.community = row.community, "
                + "e.evidence = row.evidence";
        for (int i = 0; i < rows.size(); i += CHUNK) {
            List<Map<String, Object>> part = new ArrayList<>(rows.subList(i, Math.min(rows.size(), i + CHUNK)));
            db.write(writeBack, Map.of("rows", part));
        }

        Summary summary = new Summary(graph.vertexSet().size(), graph.edgeSet().size(), communityCount, method);
        audit.log(username, "RUN_ANALYSIS", summary.entities() + " entities, " + summary.links() + " links, "
                + summary.communities() + " groups; scoring used " + summary.eigenvectorMethod());
        return summary;
    }

    // ------------------------------------------------------------------ helpers

    /** Scales values to 0..1. All-equal values become 0. */
    private static Map<String, Double> normalize(Map<String, Double> raw) {
        Map<String, Double> out = new HashMap<>();
        if (raw.isEmpty()) {
            return out;
        }
        double min = Collections.min(raw.values());
        double max = Collections.max(raw.values());
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            out.put(e.getKey(), max > min ? (e.getValue() - min) / (max - min) : 0.0);
        }
        return out;
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    /**
     * Label propagation with a fixed processing order and fixed tie-breaking, so the result is the same on every
     * run. Returns entity key to community number (0 is the largest community).
     */
    private static Map<String, Integer> detectCommunities(Graph<String, DefaultEdge> graph) {
        List<String> order = new ArrayList<>(graph.vertexSet());
        Collections.sort(order);
        Map<String, String> label = new HashMap<>();
        for (String v : order) {
            label.put(v, v);
        }
        for (int iteration = 0; iteration < 30; iteration++) {
            boolean changed = false;
            for (String v : order) {
                Map<String, Integer> counts = new HashMap<>();
                for (DefaultEdge e : graph.edgesOf(v)) {
                    counts.merge(label.get(Graphs.getOppositeVertex(graph, e, v)), 1, Integer::sum);
                }
                if (counts.isEmpty()) {
                    continue;
                }
                String best = null;
                int bestCount = -1;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    int count = entry.getValue();
                    if (count > bestCount || (count == bestCount && entry.getKey().compareTo(best) < 0)) {
                        best = entry.getKey();
                        bestCount = count;
                    }
                }
                if (!best.equals(label.get(v))) {
                    label.put(v, best);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Map<String, Integer> sizes = new HashMap<>();
        for (String l : label.values()) {
            sizes.merge(l, 1, Integer::sum);
        }
        List<String> labelsBySize = new ArrayList<>(sizes.keySet());
        labelsBySize.sort((a, b) -> {
            int bySize = Integer.compare(sizes.get(b), sizes.get(a));
            return bySize != 0 ? bySize : a.compareTo(b);
        });
        Map<String, Integer> idByLabel = new HashMap<>();
        for (int i = 0; i < labelsBySize.size(); i++) {
            idByLabel.put(labelsBySize.get(i), i);
        }
        Map<String, Integer> result = new HashMap<>();
        for (String v : order) {
            result.put(v, idByLabel.get(label.get(v)));
        }
        return result;
    }

    /** Plain-language reason for the entity's ranking: how many links, the strongest ones, and bridging. */
    private static String evidence(String v, Graph<String, DefaultEdge> graph,
                                   Map<String, List<Neighbor>> adjacency, Map<String, String> names,
                                   Map<String, Integer> community) {
        List<Neighbor> neighbors = new ArrayList<>(adjacency.getOrDefault(v, List.of()));
        neighbors.sort(Comparator.comparingDouble(Neighbor::weight).reversed().thenComparing(Neighbor::key));

        StringBuilder sb = new StringBuilder();
        sb.append("Directly linked to ").append(graph.degreeOf(v)).append(graph.degreeOf(v) == 1 ? " entity" : " entities");
        if (!neighbors.isEmpty()) {
            sb.append(". Strongest links: ");
            int shown = Math.min(3, neighbors.size());
            for (int i = 0; i < shown; i++) {
                Neighbor n = neighbors.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(names.get(n.key())).append(" (").append(n.kind()).append(", weight ")
                        .append(n.weight() == Math.rint(n.weight()) ? String.valueOf((long) n.weight())
                                : String.valueOf(n.weight()))
                        .append(')');
            }
        }
        Set<Integer> groups = new TreeSet<>();
        groups.add(community.get(v));
        for (Neighbor n : neighbors) {
            groups.add(community.get(n.key()));
        }
        if (groups.size() > 1) {
            sb.append(". Acts as a cross-network bridge across suspect circles");
        }
        return sb.append('.').toString();
    }
}

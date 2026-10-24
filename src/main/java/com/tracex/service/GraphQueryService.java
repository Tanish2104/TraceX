package com.tracex.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tracex.dto.GraphData;
import com.tracex.dto.KeyPerson;
import com.tracex.dto.MissingLink;
import com.tracex.dto.NeighborView;
import com.tracex.dto.PathResult;
import com.tracex.repository.Neo4jGateway;
import com.tracex.util.RelationshipFormatter;

/** Read-only questions asked of the graph, used by the dashboard pages and the JSON API. */
@Service
public class GraphQueryService {

    /** Shared RETURN clause; the property names match KeyPerson.from(). */
    private static final String RETURN_ENTITY =
            "RETURN e.key AS key, e.label AS label, e.type AS type, "
            + "coalesce(e.score, 0.0) AS score, coalesce(e.degree, 0) AS degree, "
            + "coalesce(e.betweenness, 0.0) AS betweenness, coalesce(e.closeness, 0.0) AS closeness, "
            + "coalesce(e.eigenvector, 0.0) AS eigenvector, coalesce(e.community, -1) AS community, "
            + "coalesce(e.evidence, '') AS evidence ";

    private final Neo4jGateway db;

    public GraphQueryService(Neo4jGateway db) {
        this.db = db;
    }

    public long countEntities() {
        return count("MATCH (e:Entity) RETURN count(e) AS c");
    }

    public long countLinks() {
        return count("MATCH ()-[r:LINKED]->() RETURN count(r) AS c");
    }

    /** Highest-scoring entities. type is PERSON, PHONE, VEHICLE, ACCOUNT, LOCATION or ALL. */
    public List<KeyPerson> keyEntities(String type, int limit) {
        String cypher = "MATCH (e:Entity) WHERE e.score IS NOT NULL AND ($type = 'ALL' OR e.type = $type) "
                + RETURN_ENTITY + "ORDER BY score DESC, key ASC LIMIT $limit";
        return toKeyPersons(db.read(cypher, Map.of("type", type, "limit", (long) limit)));
    }

    public Optional<KeyPerson> entity(String key) {
        String cypher = "MATCH (e:Entity {key: $key}) " + RETURN_ENTITY;
        List<KeyPerson> rows = toKeyPersons(db.read(cypher, Map.of("key", key)));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<KeyPerson> search(String text) {
        String cypher = "MATCH (e:Entity) WHERE toLower(e.label) CONTAINS toLower($q) "
                + RETURN_ENTITY + "ORDER BY score DESC, label ASC LIMIT 50";
        return toKeyPersons(db.read(cypher, Map.of("q", text)));
    }

    /** Every entity, used to fill the drop-downs on the path page. */
    public List<KeyPerson> entityOptions() {
        String cypher = "MATCH (e:Entity) " + RETURN_ENTITY + "ORDER BY type ASC, label ASC LIMIT 2000";
        return toKeyPersons(db.read(cypher, Map.of()));
    }

    public List<NeighborView> neighbors(String key) {
        String cypher = "MATCH (a:Entity {key: $key})-[r:LINKED]-(b:Entity) "
                + "RETURN b.key AS key, b.label AS label, b.type AS type, r.kind AS kind, "
                + "coalesce(r.weight, 1.0) AS weight, coalesce(r.evidence, '') AS evidence "
                + "ORDER BY weight DESC, label ASC";
        List<NeighborView> out = new ArrayList<>();
        for (Map<String, Object> row : db.read(cypher, Map.of("key", key))) {
            out.add(new NeighborView(text(row.get("key")), text(row.get("label")), text(row.get("type")),
                    text(row.get("kind")), decimal(row.get("weight")), text(row.get("evidence"))));
        }
        return out;
    }

    /** Whole graph in the shape vis-network needs. */
    public GraphData graph() {
        List<GraphData.Node> nodes = new ArrayList<>();
        for (Map<String, Object> row : db.read(
                "MATCH (e:Entity) RETURN e.key AS key, e.label AS label, e.type AS type, "
                        + "coalesce(e.score, 0.0) AS score, coalesce(e.community, -1) AS community", Map.of())) {
            nodes.add(new GraphData.Node(text(row.get("key")), text(row.get("label")), text(row.get("type")),
                    decimal(row.get("score")), whole(row.get("community"))));
        }
        List<GraphData.Edge> edges = new ArrayList<>();
        for (Map<String, Object> row : db.read(
                "MATCH (a:Entity)-[r:LINKED]->(b:Entity) RETURN a.key AS src, b.key AS dst, r.kind AS kind, "
                        + "coalesce(r.weight, 1.0) AS weight, coalesce(r.evidence, '') AS evidence", Map.of())) {
            edges.add(new GraphData.Edge(text(row.get("src")), text(row.get("dst")), text(row.get("kind")),
                    decimal(row.get("weight")), text(row.get("evidence"))));
        }
        return new GraphData(nodes, edges);
    }

    /** Shortest chain of links (up to 8 hops) between two entities, ignoring link direction and shared districts. */
    public PathResult shortestPath(String startKey, String endKey) {
        if (startKey.equals(endKey)) {
            return PathResult.notFound("Choose two different entities.");
        }
        String cypher = "MATCH (a:Entity {key: $startKey}), (b:Entity {key: $endKey}) "
                + "MATCH p = shortestPath((a)-[:LINKED*..8]-(b)) "
                + "WHERE all(r IN relationships(p) WHERE r.kind <> 'LOCATED_AT') "
                + "RETURN [n IN nodes(p) | {key: n.key, label: n.label, type: n.type}] AS nodes, "
                + "[r IN relationships(p) | {kind: r.kind, evidence: coalesce(r.evidence, '')}] AS edges";
        List<Map<String, Object>> rows = db.read(cypher, Map.of("startKey", startKey, "endKey", endKey));
        if (rows.isEmpty()) {
            return PathResult.notFound("No chain of up to 8 links connects these two entities.");
        }
        List<?> nodeList = (List<?>) rows.get(0).get("nodes");
        List<?> edgeList = (List<?>) rows.get(0).get("edges");
        if (nodeList == null || nodeList.isEmpty()) {
            return PathResult.notFound("No chain of up to 8 links connects these two entities.");
        }

        List<PathResult.PathStep> steps = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < nodeList.size(); i++) {
            Map<?, ?> nodeMap = (Map<?, ?>) nodeList.get(i);
            String key = text(nodeMap.get("key"));
            String label = text(nodeMap.get("label"));
            String type = text(nodeMap.get("type"));

            boolean isFirst = (i == 0);
            boolean isLast = (i == nodeList.size() - 1);

            String role;
            if (isFirst) {
                role = "Origin Target";
            } else if (isLast) {
                role = "Destination Target";
            } else {
                role = "Intermediary Link";
            }

            String linkToNext = null;
            String linkRawKind = null;
            String linkEvidence = null;

            if (edgeList != null && i < edgeList.size()) {
                Map<?, ?> edgeMap = (Map<?, ?>) edgeList.get(i);
                linkRawKind = text(edgeMap.get("kind"));
                linkToNext = RelationshipFormatter.formatKind(linkRawKind);
                linkEvidence = text(edgeMap.get("evidence"));
            }

            steps.add(new PathResult.PathStep(i + 1, key, label, type, role, linkToNext, linkRawKind, linkEvidence, isLast));

            if (isLast) {
                lines.add(type + " " + label);
            } else {
                lines.add(type + " " + label + " (Linked via: " + linkToNext + ")");
            }
        }

        return PathResult.found(steps, lines);
    }

    /**
     * Discovers hidden or missing links between suspects who are not directly linked,
     * but share a common intermediary (middleman, phone, vehicle, account).
     */
    public List<MissingLink> findMissingLinks() {
        String cypher = "MATCH (a:Entity {type: 'PERSON'})-[r1:LINKED]-(shared:Entity)-[r2:LINKED]-(b:Entity {type: 'PERSON'}) "
                + "WHERE a.key < b.key AND NOT (a)-[:LINKED]-(b) AND shared.type <> 'LOCATION' "
                + "RETURN a.key AS keyA, a.label AS labelA, "
                + "       b.key AS keyB, b.label AS labelB, "
                + "       shared.key AS sharedKey, shared.label AS sharedLabel, shared.type AS sharedType, "
                + "       r1.kind AS kindA, r2.kind AS kindB "
                + "ORDER BY a.label ASC, b.label ASC LIMIT 50";
        List<MissingLink> list = new ArrayList<>();
        for (Map<String, Object> row : db.read(cypher, Map.of())) {
            String labelA = text(row.get("labelA"));
            String labelB = text(row.get("labelB"));
            String sharedLabel = text(row.get("sharedLabel"));
            String sharedType = text(row.get("sharedType"));
            String kindA = text(row.get("kindA"));
            String kindB = text(row.get("kindB"));

            String rationale = buildRationale(labelA, labelB, sharedLabel, sharedType, kindA, kindB);
            list.add(new MissingLink(
                    text(row.get("keyA")), labelA,
                    text(row.get("keyB")), labelB,
                    text(row.get("sharedKey")), sharedLabel, sharedType,
                    kindA, kindB, rationale
            ));
        }
        return list;
    }

    private static String buildRationale(String a, String b, String shared, String type, String kindA, String kindB) {
        if ("PERSON".equals(type)) {
            if ("MENTIONED_WITH".equalsIgnoreCase(kindA) && "MENTIONED_WITH".equalsIgnoreCase(kindB)) {
                return a + " and " + b + " have no direct link, but both connect to intermediary " + shared
                        + " as co-accused named in the same case investigation.";
            }
            return a + " and " + b + " have no direct link, but both connect to intermediary " + shared
                    + " (" + RelationshipFormatter.formatKind(kindA) + " & " + RelationshipFormatter.formatKind(kindB) + ").";
        } else if ("PHONE".equals(type)) {
            return a + " and " + b + " share common call communication or registered association with mobile " + shared + ".";
        } else if ("VEHICLE".equals(type)) {
            return a + " and " + b + " are both tied to vehicle registration plate " + shared + ".";
        } else if ("ACCOUNT".equals(type)) {
            return a + " and " + b + " both transact or share financial operations through bank account " + shared + ".";
        }
        return a + " and " + b + " share indirect connection via " + shared + " (" + type + ").";
    }

    // ------------------------------------------------------------------ helpers

    private long count(String cypher) {
        List<Map<String, Object>> rows = db.read(cypher, Map.of());
        return rows.isEmpty() ? 0L : whole(rows.get(0).get("c"));
    }

    private static List<KeyPerson> toKeyPersons(List<Map<String, Object>> rows) {
        List<KeyPerson> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(KeyPerson.from(row));
        }
        return out;
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private static double decimal(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    private static long whole(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }
}

package com.tracex.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tracex.dto.GraphBatch;
import com.tracex.repository.Neo4jGateway;

/**
 * Writes a GraphBatch to Neo4j. Every node has the label Entity and a unique "key" property.
 * Every edge has the type LINKED and a "kind" property (OWNS, CALLED, TRANSFERRED, LOCATED_AT,
 * MENTIONED_WITH). Writing the same file twice does not double the weights: the larger value wins.
 */
@Service
public class GraphWriter {

    private static final int CHUNK = 500;

    private static final String NODE_CYPHER =
            "UNWIND $rows AS row "
            + "MERGE (e:Entity {key: row.key}) "
            + "ON CREATE SET e.type = row.type, e.label = row.label "
            + "ON MATCH SET e.label = CASE WHEN size(row.label) > size(coalesce(e.label, '')) "
            + "THEN row.label ELSE e.label END";

    private static final String EDGE_CYPHER =
            "UNWIND $rows AS row "
            + "MATCH (a:Entity {key: row.srcKey}) "
            + "MATCH (b:Entity {key: row.dstKey}) "
            + "MERGE (a)-[r:LINKED {kind: row.kind}]->(b) "
            + "ON CREATE SET r.weight = row.weight, r.evidence = row.evidence "
            + "ON MATCH SET r.weight = CASE WHEN row.weight > r.weight THEN row.weight ELSE r.weight END";

    private final Neo4jGateway db;

    public GraphWriter(Neo4jGateway db) {
        this.db = db;
    }

    public void write(GraphBatch batch) {
        for (List<Map<String, Object>> part : partition(batch.nodeRows())) {
            db.write(NODE_CYPHER, Map.of("rows", part));
        }
        for (List<Map<String, Object>> part : partition(batch.edgeRows())) {
            db.write(EDGE_CYPHER, Map.of("rows", part));
        }
    }

    private static <T> List<List<T>> partition(List<T> list) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += CHUNK) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + CHUNK))));
        }
        return parts;
    }
}

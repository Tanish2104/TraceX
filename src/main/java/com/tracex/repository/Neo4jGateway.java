package com.tracex.repository;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the Neo4j Java driver. Spring Boot creates the Driver bean from the
 * spring.neo4j.* properties. Every graph query in the application goes through this class.
 */
@Component
public class Neo4jGateway {

    private final Driver driver;

    public Neo4jGateway(Driver driver) {
        this.driver = driver;
    }

    /** Runs a read query and returns each record as a map keyed by the RETURN aliases. */
    public List<Map<String, Object>> read(String cypher, Map<String, Object> params) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run(cypher, params).list(record -> record.asMap()));
        }
    }

    /** Runs a write query in its own transaction. */
    public void write(String cypher, Map<String, Object> params) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, params).consume();
                return null;
            });
        }
    }
}

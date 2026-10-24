package com.tracex.dto;

import java.util.Map;

/** One ranked entity with its scores and the evidence behind them. */
public class KeyPerson {

    private String key;
    private String label;
    private String type;
    private double score;
    private long degree;
    private double betweenness;
    private double closeness;
    private double eigenvector;
    private long community;
    private String evidence;
    private String decision = "PENDING";

    /** Builds an instance from a row returned by the Cypher RETURN clause in GraphQueryService. */
    public static KeyPerson from(Map<String, Object> row) {
        KeyPerson k = new KeyPerson();
        k.key = text(row.get("key"));
        k.label = text(row.get("label"));
        k.type = text(row.get("type"));
        k.score = number(row.get("score")).doubleValue();
        k.degree = number(row.get("degree")).longValue();
        k.betweenness = number(row.get("betweenness")).doubleValue();
        k.closeness = number(row.get("closeness")).doubleValue();
        k.eigenvector = number(row.get("eigenvector")).doubleValue();
        k.community = number(row.get("community")).longValue();
        k.evidence = text(row.get("evidence"));
        return k;
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Number number(Object o) {
        return o instanceof Number ? (Number) o : Double.valueOf(0);
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

    public double getScore() {
        return score;
    }

    public long getDegree() {
        return degree;
    }

    public double getBetweenness() {
        return betweenness;
    }

    public double getCloseness() {
        return closeness;
    }

    public double getEigenvector() {
        return eigenvector;
    }

    public long getCommunity() {
        return community;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}

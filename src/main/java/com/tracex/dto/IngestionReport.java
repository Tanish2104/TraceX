package com.tracex.dto;

import java.util.ArrayList;
import java.util.List;

/** Result of one file upload, shown to the user on the upload page. */
public class IngestionReport {

    private final String title;
    private int rowsRead;
    private int rowsAccepted;
    private int nodes;
    private int edges;
    private final List<String> errors = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public IngestionReport(String title) {
        this.title = title;
    }

    public void rowRead() {
        rowsRead++;
    }

    public void rowAccepted() {
        rowsAccepted++;
    }

    public void error(String message) {
        errors.add(message);
    }

    public void note(String message) {
        notes.add(message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getTitle() {
        return title;
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getRowsAccepted() {
        return rowsAccepted;
    }

    public int getNodes() {
        return nodes;
    }

    public void setNodes(int nodes) {
        this.nodes = nodes;
    }

    public int getEdges() {
        return edges;
    }

    public void setEdges(int edges) {
        this.edges = edges;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getNotes() {
        return notes;
    }
}

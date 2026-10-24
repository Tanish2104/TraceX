package com.tracex.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracex.dto.GraphData;
import com.tracex.dto.MissingLink;
import com.tracex.service.GraphQueryService;

/** JSON endpoint used by the vis-network graph page and police missing link detection. */
@RestController
public class ApiController {

    private final GraphQueryService graph;

    public ApiController(GraphQueryService graph) {
        this.graph = graph;
    }

    @GetMapping("/api/graph")
    public GraphData graph() {
        return graph.graph();
    }

    @GetMapping("/api/missing-links")
    public List<MissingLink> missingLinks() {
        return graph.findMissingLinks();
    }
}

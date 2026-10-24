package com.tracex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the graph page. The page loads its data from /api/graph (see ApiController). */
@Controller
public class GraphController {

    @GetMapping("/graph")
    public String page() {
        return "graph";
    }
}

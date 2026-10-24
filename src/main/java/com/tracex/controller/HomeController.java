package com.tracex.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.tracex.dto.KeyPerson;
import com.tracex.service.GraphQueryService;
import com.tracex.service.ReviewService;
import com.tracex.util.EntityTypes;

@Controller
public class HomeController {

    private final GraphQueryService graph;
    private final ReviewService review;

    public HomeController(GraphQueryService graph, ReviewService review) {
        this.graph = graph;
        this.review = review;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("entities", graph.countEntities());
        model.addAttribute("links", graph.countLinks());
        List<KeyPerson> top = graph.keyEntities(EntityTypes.PERSON, 5);
        review.applyDecisions(top);
        model.addAttribute("top", top);
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", defaultValue = "") String q, Model model) {
        String query = q.trim();
        List<KeyPerson> results = query.isEmpty() ? List.of() : graph.search(query);
        review.applyDecisions(results);
        model.addAttribute("heading", "Search results for \"" + query + "\"");
        model.addAttribute("hint", results.size() + " matching entities (showing at most 50).");
        model.addAttribute("list", results);
        model.addAttribute("type", EntityTypes.ALL);
        model.addAttribute("types", null);
        return "keypersons";
    }
}

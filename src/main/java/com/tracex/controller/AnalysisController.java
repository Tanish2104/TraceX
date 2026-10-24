package com.tracex.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tracex.dto.KeyPerson;
import com.tracex.service.AnalysisService;
import com.tracex.service.GraphQueryService;
import com.tracex.service.ReviewService;
import com.tracex.util.EntityTypes;

@Controller
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysis;
    private final GraphQueryService graph;
    private final ReviewService review;

    public AnalysisController(AnalysisService analysis, GraphQueryService graph, ReviewService review) {
        this.analysis = analysis;
        this.graph = graph;
        this.review = review;
    }

    @PostMapping("/run")
    public String run(Principal principal, RedirectAttributes redirect) {
        try {
            AnalysisService.Summary s = analysis.run(principal.getName());
            redirect.addFlashAttribute("message", "Analysis finished: " + s.entities() + " entities, "
                    + s.links() + " links, " + s.communities() + " groups. Scoring used " + s.eigenvectorMethod() + ".");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/analysis/key";
    }

    @GetMapping("/key")
    public String key(@RequestParam(value = "type", defaultValue = "PERSON") String type, Model model) {
        String selected = EntityTypes.FILTERS.contains(type.toUpperCase()) ? type.toUpperCase() : EntityTypes.PERSON;
        List<KeyPerson> list = graph.keyEntities(selected, 25);
        review.applyDecisions(list);
        model.addAttribute("heading", "Highest-ranked entities");
        model.addAttribute("hint", "Nothing here counts as a finding until an officer accepts it. "
                + "Open an entity to see the evidence behind its score.");
        model.addAttribute("list", list);
        model.addAttribute("type", selected);
        model.addAttribute("types", EntityTypes.FILTERS);
        return "keypersons";
    }

    @GetMapping("/entity")
    public String entity(@RequestParam("key") String key, Model model) {
        KeyPerson entity = graph.entity(key).orElse(null);
        if (entity == null) {
            model.addAttribute("message", "No entity with key " + key + " exists in the graph.");
            return "error";
        }
        review.applyDecisions(List.of(entity));
        model.addAttribute("entity", entity);
        model.addAttribute("neighbors", graph.neighbors(key));
        return "entity";
    }

    @GetMapping("/path")
    public String path(@RequestParam(value = "from", required = false) String from,
                       @RequestParam(value = "to", required = false) String to, Model model) {
        model.addAttribute("options", graph.entityOptions());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            model.addAttribute("path", graph.shortestPath(from, to));
        }
        model.addAttribute("missingLinks", graph.findMissingLinks());
        return "path";
    }
}

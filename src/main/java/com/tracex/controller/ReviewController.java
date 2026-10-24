package com.tracex.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tracex.service.ReviewService;
import com.tracex.util.EntityTypes;

/** Accept or reject a flagged entity. Allowed for ANALYST and SUPERVISOR only (see SecurityConfig). */
@Controller
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService review;

    public ReviewController(ReviewService review) {
        this.review = review;
    }

    @PostMapping
    public String decide(@RequestParam("key") String key,
                         @RequestParam("label") String label,
                         @RequestParam("decision") String decision,
                         @RequestParam(value = "note", required = false) String note,
                         @RequestParam(value = "back", defaultValue = "list") String back,
                         @RequestParam(value = "type", defaultValue = "PERSON") String type,
                         Principal principal, RedirectAttributes redirect) {
        try {
            review.decide(key, label, decision, note, principal.getName());
            redirect.addFlashAttribute("message",
                    "Recorded: " + decision.toLowerCase() + " for " + label + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        if ("entity".equals(back)) {
            return "redirect:/analysis/entity?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        }
        String filter = EntityTypes.FILTERS.contains(type.toUpperCase()) ? type.toUpperCase() : EntityTypes.PERSON;
        return "redirect:/analysis/key?type=" + filter;
    }
}

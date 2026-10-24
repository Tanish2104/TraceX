package com.tracex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.tracex.service.AuditService;

/** Read-only view of the audit trail. Allowed for AUDITOR and SUPERVISOR only (see SecurityConfig). */
@Controller
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("entries", audit.latest());
        return "audit";
    }
}

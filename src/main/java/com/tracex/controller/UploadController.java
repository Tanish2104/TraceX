package com.tracex.controller;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.tracex.dto.IngestionReport;
import com.tracex.service.AnalysisService;
import com.tracex.service.IngestionService;
import com.tracex.service.NlpClient;

@Controller
@RequestMapping("/upload")
public class UploadController {

    private static final String FIR = "fir";
    private static final String CALLS = "calls";
    private static final String TRANSACTIONS = "transactions";

    private final IngestionService ingestion;
    private final NlpClient nlp;
    private final AnalysisService analysis;

    public UploadController(IngestionService ingestion, NlpClient nlp, AnalysisService analysis) {
        this.ingestion = ingestion;
        this.nlp = nlp;
        this.analysis = analysis;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("nlpStatus", nlp.status());
        return "upload";
    }

    @PostMapping
    public String upload(@RequestParam(value = "files", required = false) MultipartFile[] files,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "fir", required = false) MultipartFile fir,
                         @RequestParam(value = "calls", required = false) MultipartFile calls,
                         @RequestParam(value = "transactions", required = false) MultipartFile transactions,
                         @RequestParam(value = "clearPrevious", defaultValue = "true") boolean clearPrevious,
                         Principal principal, Model model) {
        List<IngestionReport> reports = new ArrayList<>();

        // If starting a fresh case (default true), clear old case records so graph only contains this new case
        if (clearPrevious) {
            ingestion.clearGraph(principal.getName());
        }

        // 1. Process unified / available files uploaded
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    reports.add(processAuto(f, principal.getName()));
                }
            }
        }
        if (file != null && !file.isEmpty()) {
            reports.add(processAuto(file, principal.getName()));
        }

        // 2. Process legacy categorized uploads if present
        addIfPresent(reports, FIR, fir, principal.getName());
        addIfPresent(reports, CALLS, calls, principal.getName());
        addIfPresent(reports, TRANSACTIONS, transactions, principal.getName());

        if (reports.isEmpty()) {
            model.addAttribute("error", "Choose at least one investigation file to upload.");
        } else {
            // Auto-run centrality and community detection for the new case
            try {
                analysis.run(principal.getName());
            } catch (Exception ex) {
                // If graph has no edges yet, ignore
            }
        }
        model.addAttribute("reports", reports);
        model.addAttribute("nlpStatus", nlp.status());
        return "upload";
    }

    @PostMapping("/reset")
    public String reset(Principal principal, Model model) {
        ingestion.clearGraph(principal.getName());
        model.addAttribute("message", "Investigation workspace reset successfully. Old case data cleared.");
        model.addAttribute("nlpStatus", nlp.status());
        return "upload";
    }

    private IngestionReport processAuto(MultipartFile file, String user) {
        try (InputStream in = file.getInputStream()) {
            return ingestion.ingestAuto(in, file.getOriginalFilename(), user);
        } catch (IOException ex) {
            IngestionReport failed = new IngestionReport(file.getOriginalFilename() != null ? file.getOriginalFilename() : "Upload");
            failed.error("The file could not be read: " + ex.getMessage());
            return failed;
        }
    }

    @PostMapping("/sample")
    public String loadSample(Principal principal, Model model) {
        ingestion.clearGraph(principal.getName());
        List<IngestionReport> reports = new ArrayList<>();
        reports.add(processSample(FIR, "sample/fir.csv", principal.getName()));
        reports.add(processSample(CALLS, "sample/calls.csv", principal.getName()));
        reports.add(processSample(TRANSACTIONS, "sample/transactions.csv", principal.getName()));
        try {
            analysis.run(principal.getName());
        } catch (Exception ex) {
            // ignore
        }
        model.addAttribute("reports", reports);
        model.addAttribute("nlpStatus", nlp.status());
        return "upload";
    }

    private void addIfPresent(List<IngestionReport> reports, String kind, MultipartFile file, String user) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            reports.add(process(kind, file.getInputStream(), user));
        } catch (IOException ex) {
            IngestionReport failed = new IngestionReport(titleOf(kind));
            failed.error("The upload could not be read: " + ex.getMessage());
            reports.add(failed);
        }
    }

    private IngestionReport processSample(String kind, String path, String user) {
        try {
            return process(kind, new ClassPathResource(path).getInputStream(), user);
        } catch (IOException ex) {
            IngestionReport failed = new IngestionReport(titleOf(kind));
            failed.error("The sample file could not be opened: " + ex.getMessage());
            return failed;
        }
    }

    private IngestionReport process(String kind, InputStream in, String user) {
        try (in) {
            switch (kind) {
                case FIR:
                    return ingestion.ingestFir(in, user);
                case CALLS:
                    return ingestion.ingestCalls(in, user);
                default:
                    return ingestion.ingestTransactions(in, user);
            }
        } catch (Exception ex) {
            IngestionReport failed = new IngestionReport(titleOf(kind));
            failed.error("Ingestion failed: " + ex.getMessage());
            return failed;
        }
    }

    private static String titleOf(String kind) {
        switch (kind) {
            case FIR:
                return "FIR records";
            case CALLS:
                return "Call records";
            default:
                return "Bank transactions";
        }
    }
}
package com.tracex.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.tracex.dto.GraphBatch;
import com.tracex.dto.IngestionReport;
import com.tracex.repository.Neo4jGateway;
import com.tracex.util.EntityTypes;
import com.tracex.util.KeyUtil;

/**
 * Stage 1 and 2: read CSV files, validate them, extract entities and write them to the graph.
 * Column names are matched case-insensitively. Bad rows are skipped and listed in the report.
 */
@Service
public class IngestionService {

    private final GraphWriter writer;
    private final Neo4jGateway db;
    private final AiExtractionService aiExtraction;
    private final AuditService audit;

    public IngestionService(GraphWriter writer, Neo4jGateway db, AiExtractionService aiExtraction, AuditService audit) {
        this.writer = writer;
        this.db = db;
        this.aiExtraction = aiExtraction;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ FIR records

    /**
     * Clears all entities and relationships from the graph database.
     * Used when starting a fresh investigation / uploading a new case so old and new data don't merge.
     */
    public void clearGraph(String user) {
        db.write("MATCH (n) DETACH DELETE n", Map.of());
        audit.log(user, "CLEAR_GRAPH", "All entities and relationships cleared to start a fresh case investigation.");
    }

    /** Columns: firId, accused (required); date, district, phone, vehicle, account, narrative (optional). */
    public IngestionReport ingestFir(InputStream in, String user) {
        IngestionReport report = new IngestionReport("FIR records");
        List<Map<String, String>> rows = readCsv(in, List.of("firid", "accused"), report);
        if (report.hasErrors()) {
            audit.log(user, "INGEST_FIR_REJECTED", String.join(" | ", report.getErrors()));
            return report;
        }
        if (rows.isEmpty()) {
            report.note("The file has a header but no data rows.");
            return report;
        }

        EntityResolver resolver = loadResolver();
        GraphBatch batch = new GraphBatch();

        // Cross-reference other accused mentioned in narratives (vital for case syndicate linking)
        Set<String> allFileAccused = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String a = row.get("accused");
            if (a != null && !a.isBlank()) {
                allFileAccused.add(a.trim());
            }
        }

        // One call for the whole file. Uses the AI service when it is running, pattern matching otherwise.
        List<String> narratives = new ArrayList<>();
        for (Map<String, String> row : rows) {
            narratives.add(row.get("narrative"));
        }
        AiExtractionService.Batch extraction = aiExtraction.extractAll(narratives);
        report.note("Entity extraction from narratives used: " + extraction.getMode() + ".");

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            report.rowRead();
            String line = row.get("_line");
            String accused = row.get("accused");
            if (accused == null || accused.isBlank()) {
                report.error("Line " + line + ": the accused name is empty. Row skipped.");
                continue;
            }

            String evidence = "FIR " + row.getOrDefault("firid", "?");
            EntityResolver.Resolved person = resolver.resolve(accused);
            batch.addNode(person.key(), EntityTypes.PERSON, person.label());

            // Structured columns: the accused OWNS these.
            Set<String> owned = new HashSet<>();
            String phone = row.get("phone");
            addOwned(batch, owned, person.key(), EntityTypes.PHONE, KeyUtil.normalizePhone(phone), phone,
                    evidence, line, report);
            String vehicle = row.get("vehicle");
            addOwned(batch, owned, person.key(), EntityTypes.VEHICLE, KeyUtil.normalizePlate(vehicle), vehicle,
                    evidence, line, report);
            String account = row.get("account");
            addOwned(batch, owned, person.key(), EntityTypes.ACCOUNT, KeyUtil.normalizeAccount(account), account,
                    evidence, line, report);

            String district = row.get("district");
            if (district != null && !district.isBlank()) {
                String locationKey = KeyUtil.key(EntityTypes.LOCATION, KeyUtil.normalizeText(district));
                batch.addNode(locationKey, EntityTypes.LOCATION, district.trim());
                batch.addEdge(person.key(), locationKey, "LOCATED_AT", 1.0, evidence);
            }

            String organization = row.get("organization");
            if (organization != null && !organization.isBlank()) {
                String orgKey = KeyUtil.key(EntityTypes.LOCATION, KeyUtil.normalizeText(organization));
                batch.addNode(orgKey, EntityTypes.LOCATION, organization.trim());
                batch.addEdge(person.key(), orgKey, "ASSOCIATED_WITH", 1.0, evidence);
            }

            // Free text: whatever the narrative mentions is linked with MENTIONED_WITH (people, phones, plates,
            // accounts) or LOCATED_AT (places). The evidence records which method found it.
            EntityExtractor.Extracted found = extraction.getResults().get(i);

            // Cross-reference any other co-accused mentioned in this accused's narrative
            String narrative = row.get("narrative");
            if (narrative != null && !narrative.isBlank()) {
                for (String otherAccused : allFileAccused) {
                    if (!otherAccused.equalsIgnoreCase(accused)) {
                        String[] parts = otherAccused.split("\\s+");
                        String surname = parts[parts.length - 1];
                        if (narrative.toLowerCase().contains(otherAccused.toLowerCase())
                                || (surname.length() > 4 && narrative.toLowerCase().contains(surname.toLowerCase()))) {
                            found.persons.add(otherAccused);
                        }
                    }
                }
            }
            for (String p : found.phones) {
                addMention(batch, owned, person.key(), EntityTypes.PHONE, p,
                        narrativeEvidence(evidence, found.sourceOf(EntityTypes.PHONE, p)));
            }
            for (String p : found.plates) {
                addMention(batch, owned, person.key(), EntityTypes.VEHICLE, p,
                        narrativeEvidence(evidence, found.sourceOf(EntityTypes.VEHICLE, p)));
            }
            for (String a : found.accounts) {
                addMention(batch, owned, person.key(), EntityTypes.ACCOUNT, a,
                        narrativeEvidence(evidence, found.sourceOf(EntityTypes.ACCOUNT, a)));
            }
            for (String name : found.persons) {
                EntityResolver.Resolved other = resolver.resolve(name);
                batch.addNode(other.key(), EntityTypes.PERSON, other.label());
                batch.addEdge(person.key(), other.key(), "MENTIONED_WITH", 1.0,
                        narrativeEvidence(evidence, found.sourceOf(EntityTypes.PERSON, name)));
            }
            for (String place : found.places) {
                String placeKey = KeyUtil.key(EntityTypes.LOCATION, KeyUtil.normalizeText(place));
                batch.addNode(placeKey, EntityTypes.LOCATION, place);
                batch.addEdge(person.key(), placeKey, "LOCATED_AT", 1.0,
                        narrativeEvidence(evidence, found.sourceOf(EntityTypes.LOCATION, place)));
            }
            report.rowAccepted();
        }

        return finish(report, batch, resolver, user, "INGEST_FIR");
    }

    // ------------------------------------------------------------------ Call records

    /** Columns: callerPhone, receiverPhone (required); callCount, lastCallDate (optional). */
    public IngestionReport ingestCalls(InputStream in, String user) {
        IngestionReport report = new IngestionReport("Call records");
        List<Map<String, String>> rows = readCsv(in, List.of("callerphone", "receiverphone"), report);
        if (report.hasErrors()) {
            audit.log(user, "INGEST_CALLS_REJECTED", String.join(" | ", report.getErrors()));
            return report;
        }
        if (rows.isEmpty()) {
            report.note("The file has a header but no data rows.");
            return report;
        }

        GraphBatch batch = new GraphBatch();
        for (Map<String, String> row : rows) {
            report.rowRead();
            String line = row.get("_line");
            String caller = KeyUtil.normalizePhone(row.get("callerphone"));
            String receiver = KeyUtil.normalizePhone(row.get("receiverphone"));
            if (caller == null || receiver == null) {
                report.error("Line " + line + ": caller or receiver is not a valid mobile number. Row skipped.");
                continue;
            }
            if (caller.equals(receiver)) {
                report.error("Line " + line + ": caller and receiver are the same number. Row skipped.");
                continue;
            }
            String callerKey = KeyUtil.key(EntityTypes.PHONE, caller);
            String receiverKey = KeyUtil.key(EntityTypes.PHONE, receiver);
            batch.addNode(callerKey, EntityTypes.PHONE, caller);
            batch.addNode(receiverKey, EntityTypes.PHONE, receiver);
            int count = parseCount(row.get("callcount"));
            batch.addEdge(callerKey, receiverKey, "CALLED", count,
                    "Call records, last call " + row.getOrDefault("lastcalldate", "unknown"));
            report.rowAccepted();
        }
        return finish(report, batch, null, user, "INGEST_CALLS");
    }

    // ------------------------------------------------------------------ Bank transactions

    /** Columns: fromAccount, toAccount (required); amount, date (optional). Weight is the number of transfers. */
    public IngestionReport ingestTransactions(InputStream in, String user) {
        IngestionReport report = new IngestionReport("Bank transactions");
        List<Map<String, String>> rows = readCsv(in, List.of("fromaccount", "toaccount"), report);
        if (report.hasErrors()) {
            audit.log(user, "INGEST_TXN_REJECTED", String.join(" | ", report.getErrors()));
            return report;
        }
        if (rows.isEmpty()) {
            report.note("The file has a header but no data rows.");
            return report;
        }

        GraphBatch batch = new GraphBatch();
        for (Map<String, String> row : rows) {
            report.rowRead();
            String line = row.get("_line");
            String from = KeyUtil.normalizeAccount(row.get("fromaccount"));
            String to = KeyUtil.normalizeAccount(row.get("toaccount"));
            if (from == null || to == null) {
                report.error("Line " + line + ": from or to account is not a valid 9 to 18 digit number. Row skipped.");
                continue;
            }
            if (from.equals(to)) {
                report.error("Line " + line + ": from and to account are the same. Row skipped.");
                continue;
            }
            String fromKey = KeyUtil.key(EntityTypes.ACCOUNT, from);
            String toKey = KeyUtil.key(EntityTypes.ACCOUNT, to);
            batch.addNode(fromKey, EntityTypes.ACCOUNT, from);
            batch.addNode(toKey, EntityTypes.ACCOUNT, to);
            batch.addEdge(fromKey, toKey, "TRANSFERRED", 1.0,
                    "Bank transfer of INR " + row.getOrDefault("amount", "unknown"));
            report.rowAccepted();
        }
        return finish(report, batch, null, user, "INGEST_TXN");
    }

    // ------------------------------------------------------------------ helpers

    private IngestionReport finish(IngestionReport report, GraphBatch batch, EntityResolver resolver,
                                   String user, String action) {
        writer.write(batch);
        report.setNodes(batch.nodeCount());
        report.setEdges(batch.edgeCount());
        if (resolver != null) {
            for (String merge : resolver.getMergeLog()) {
                report.note(merge);
            }
        }
        audit.log(user, action, report.getTitle() + ": " + report.getRowsAccepted() + " of "
                + report.getRowsRead() + " rows accepted, " + batch.nodeCount() + " entities, "
                + batch.edgeCount() + " links, " + report.getErrors().size() + " rows with problems");
        return report;
    }

    private EntityResolver loadResolver() {
        EntityResolver resolver = new EntityResolver();
        List<Map<String, Object>> existing = db.read(
                "MATCH (e:Entity {type: 'PERSON'}) RETURN e.key AS key, e.label AS label ORDER BY e.key",
                Map.of());
        for (Map<String, Object> row : existing) {
            resolver.register(String.valueOf(row.get("key")), String.valueOf(row.get("label")));
        }
        return resolver;
    }

    private void addOwned(GraphBatch batch, Set<String> owned, String personKey, String type,
                          String normalized, String raw, String evidence, String line, IngestionReport report) {
        if (normalized == null) {
            if (raw != null && !raw.isBlank()) {
                report.error("Line " + line + ": \"" + raw + "\" is not a valid " + type.toLowerCase()
                        + " value and was ignored.");
            }
            return;
        }
        String key = KeyUtil.key(type, normalized);
        batch.addNode(key, type, normalized);
        batch.addEdge(personKey, key, "OWNS", 1.0, evidence);
        owned.add(key);
    }

    private void addMention(GraphBatch batch, Set<String> owned, String personKey, String type,
                            String value, String evidence) {
        String key = KeyUtil.key(type, value);
        if (owned.contains(key)) {
            return; // already linked as OWNS, no need to repeat it as a mention
        }
        batch.addNode(key, type, value);
        batch.addEdge(personKey, key, "MENTIONED_WITH", 1.0, evidence);
    }

    private static String narrativeEvidence(String evidence, String source) {
        return evidence + " narrative (" + source + ")";
    }

    private static int parseCount(String text) {
        if (text == null) {
            return 1;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : 1;
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /**
     * Intelligently auto-detects the data format of any uploaded investigation CSV,
     * allowing analysts to upload available case data without needing to sort into rigid categories.
     */
    public IngestionReport ingestAuto(InputStream in, String filename, String user) {
        try {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                IngestionReport report = new IngestionReport(filename != null ? filename : "Uploaded Data");
                report.error("The uploaded file is empty.");
                return report;
            }

            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }

            String[] header;
            try (CSVReader reader = new CSVReader(new StringReader(text))) {
                header = reader.readNext();
            }

            if (header == null) {
                IngestionReport report = new IngestionReport(filename != null ? filename : "Uploaded Data");
                report.error("The uploaded file has no header row.");
                return report;
            }

            List<String> cols = new ArrayList<>();
            for (String h : header) {
                cols.add(canonicalColumn(h));
            }

            boolean hasCallCols = cols.contains("callerphone") && cols.contains("receiverphone");
            boolean hasTxnCols = cols.contains("fromaccount") && cols.contains("toaccount");
            boolean hasPersonCols = cols.contains("accused");

            if (hasCallCols && !hasPersonCols) {
                return ingestCalls(new java.io.ByteArrayInputStream(bytes), user);
            } else if (hasTxnCols && !hasPersonCols) {
                return ingestTransactions(new java.io.ByteArrayInputStream(bytes), user);
            } else {
                return ingestFir(new java.io.ByteArrayInputStream(bytes), user);
            }
        } catch (Exception ex) {
            IngestionReport failed = new IngestionReport(filename != null ? filename : "Investigation Data");
            failed.error("Failed to process uploaded file: " + ex.getMessage());
            return failed;
        }
    }

    /**
     * Reads a CSV into maps keyed by lower-case column name. Adds "_line" (the file line number)
     * to every row. Missing required columns are reported as errors and no rows are returned.
     */
    private List<Map<String, String>> readCsv(InputStream in, List<String> required, IngestionReport report) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            try (CSVReader reader = new CSVReader(new StringReader(text))) {
                String[] header = reader.readNext();
                if (header == null) {
                    report.error("The file is empty.");
                    return rows;
                }
                List<String> columns = new ArrayList<>();
                for (String h : header) {
                    columns.add(canonicalColumn(h));
                }
                for (String name : required) {
                    if (!columns.contains(name)) {
                        report.error("Missing required column: " + name + " (found: " + String.join(", ", columns) + ")");
                    }
                }
                if (report.hasErrors()) {
                    return rows;
                }
                String[] line;
                int lineNumber = 1;
                while ((line = reader.readNext()) != null) {
                    lineNumber++;
                    if (line.length == 1 && line[0].isBlank()) {
                        continue; // blank line
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < columns.size(); i++) {
                        row.put(columns.get(i), i < line.length ? line[i].trim() : "");
                    }
                    row.put("_line", String.valueOf(lineNumber));
                    rows.add(row);
                }
            }
        } catch (IOException | CsvValidationException ex) {
            report.error("The file could not be read: " + ex.getMessage());
        }
        return rows;
    }

    private static String canonicalColumn(String h) {
        String clean = h.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        switch (clean) {
            case "accused":
            case "suspect":
            case "name":
            case "person":
            case "accusedname":
            case "perpetrator":
                return "accused";
            case "firid":
            case "caseid":
            case "caseno":
            case "firno":
            case "id":
            case "dossier":
                return "firid";
            case "phone":
            case "mobile":
            case "contact":
            case "cell":
            case "phoneno":
                return "phone";
            case "vehicle":
            case "plate":
            case "vehicleno":
            case "car":
            case "plateno":
                return "vehicle";
            case "account":
            case "accountno":
            case "bankaccount":
            case "accno":
            case "bank":
                return "account";
            case "narrative":
            case "notes":
            case "details":
            case "description":
            case "summary":
            case "statement":
                return "narrative";
            case "district":
            case "location":
            case "city":
            case "area":
                return "district";
            case "callerphone":
            case "caller":
            case "fromphone":
            case "callingnumber":
                return "callerphone";
            case "receiverphone":
            case "receiver":
            case "tophone":
            case "callednumber":
                return "receiverphone";
            case "fromaccount":
            case "fromacc":
            case "sourceaccount":
            case "senderaccount":
                return "fromaccount";
            case "toaccount":
            case "toacc":
            case "targetaccount":
            case "destinationaccount":
                return "toaccount";
            default:
                return clean;
        }
    }
}
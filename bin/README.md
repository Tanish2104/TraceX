# TraceX: step-by-step build guide

Criminal network analysis with explainable ranking and human review.
Stack: Java 17, Spring Boot 3.3, Thymeleaf, HTML/CSS/JS, Neo4j (graph), H2 (users, audit log, decisions), JGraphT (analysis).

---------------------------------------------------------------------

## 0. One-time setup

1. Install **JDK 17** (Temurin or any OpenJDK 17+).
2. Install **Eclipse IDE for Enterprise Java and Web Developers** (2023-09 or newer).
3. Install **Neo4j Desktop** (or create a free Aura instance). Create a local database, set a password, and start it.
   Default address is `bolt://localhost:7687`, user `neo4j`.
4. In Eclipse: **Window > Preferences > Java > Installed JREs**, make sure a JDK 17 (not a JRE) is the default.
5. Optional but handy: Help > Eclipse Marketplace > search **Spring Tools 4** and install it.

## 1. Import and run

1. Unzip `tracex-project.zip`.
2. Eclipse: **File > Import > Maven > Existing Maven Projects** > choose the `tracex` folder > Finish. Wait for Maven to download dependencies (bottom-right progress bar).
3. Open `src/main/resources/application.properties` and set `spring.neo4j.authentication.password` to your Neo4j password.
4. Right-click `TraceXApplication.java` > **Run As > Java Application** (or **Spring Boot App** if Spring Tools is installed).
5. Open <http://localhost:8080> and sign in.

| Username | Password | What this role can do |
|---|---|---|
| analyst | analyst123 | Upload, run analysis, accept or reject flags |
| supervisor | super123 | Everything an analyst can, plus the audit log |
| auditor | audit123 | Read-only results, plus the audit log |

Change these in `DataInitializer.java` before any real use.

## 2. Try the whole flow in two minutes

1. Sign in as `analyst`. Go to **Upload** and click **Load sample data**. You should see three reports (7 FIR rows, 6 call rows, 5 transfers) and a note that "R. Patil" was merged into "Rahul Patil".
2. On the dashboard click **Run analysis**.
3. On **Key entities** you will see ranked people. Open one to read the evidence. Click **Accept** or **Reject**.
4. Open **Graph** to see the network. Node size shows the score. Double-click a node to open its details.
5. Open **Find a link** and pick two people to see the chain between them.
6. Sign out, sign in as `auditor`, open **Audit log** to see every step you just did.

The sample case has planted links you can check against: one person written two ways (R. Patil / Rahul Patil), one shared phone, two people sharing a vehicle, a middleman (Suresh Kale) whom four people call, and an unrelated case (Deepak Pawar) that must stay in its own group.

---------------------------------------------------------------------

## 3. Build order (create the files in this order)

Each step only uses classes from earlier steps, so the project compiles after every step.
Paths are under `src/main/java/com/tracex/` unless they start with `src/`.

### Step 1: Project and databases (roadmap weeks 0-1)
`pom.xml`, `src/main/resources/application.properties`, `TraceXApplication.java`
`model/jpa/AppUser.java`, `AuditLog.java`, `ReviewDecision.java`
`repository/AppUserRepository.java`, `AuditLogRepository.java`, `ReviewDecisionRepository.java`, `Neo4jGateway.java`
`service/AuditService.java`
**Check:** Maven shows no errors. The app does not start usefully yet because nothing serves pages.

### Step 2: Login and roles (week 7 work, needed early)
`config/SecurityConfig.java`, `config/DataInitializer.java`, `config/GlobalExceptionHandler.java`
`service/CustomUserDetailsService.java`, `controller/AuthController.java`
`src/main/resources/templates/login.html`, `templates/error.html`, `templates/fragments/layout.html`, `static/css/style.css`
**Check:** Run the app. You see the sign-in page. After signing in you get an error page, which is expected until Step 5.

### Step 3: Data ingestion and entity extraction (weeks 1-3)
`util/EntityTypes.java`, `KeyUtil.java`, `Patterns.java`, `NameMatcher.java`
`dto/IngestionReport.java`, `GraphBatch.java`
`service/EntityExtractor.java`, `EntityResolver.java`, `GraphWriter.java`, `IngestionService.java`
`controller/UploadController.java`, `templates/upload.html`
`src/main/resources/sample/fir.csv`, `calls.csv`, `transactions.csv`
**Check:** Sign in as analyst, open `/upload`, click Load sample data. In Neo4j Browser run `MATCH (n) RETURN n` and you see the graph.

### Step 4: Graph analysis (weeks 4-5)
`service/AnalysisService.java`
**Check:** Used by Step 5. In Neo4j Browser after a run: `MATCH (e:Entity) RETURN e.label, e.score, e.community ORDER BY e.score DESC`.

### Step 5: Dashboard (week 6)
`dto/KeyPerson.java`, `NeighborView.java`, `PathResult.java`, `GraphData.java`
`service/GraphQueryService.java`, `ReviewService.java`
`controller/HomeController.java`, `AnalysisController.java`, `GraphController.java`, `ApiController.java`
`templates/index.html`, `keypersons.html`, `entity.html`, `path.html`, `graph.html`, `static/js/graph.js`
**Check:** Dashboard, ranking table, entity detail, graph and path pages all load.

### Step 6: Review gate and audit trail (week 7)
`controller/ReviewController.java`, `controller/AuditController.java`, `templates/audit.html`
`src/test/java/com/tracex/util/LogicTests.java` (run with `mvn test` or right-click > Run As > JUnit Test)
**Check:** Accept and Reject buttons work and appear in the audit log. The auditor cannot see Upload or the buttons.

---------------------------------------------------------------------

## 4. How the pieces fit

```
Upload (CSV)  ->  IngestionService  ->  EntityExtractor + EntityResolver  ->  GraphBatch  ->  GraphWriter  ->  Neo4j
                        |                                                                                      |
                   AuditService                                                                          AnalysisService (JGraphT)
                                                                                                               |
Browser  <-  Thymeleaf pages  <-  Controllers  <-  GraphQueryService / ReviewService  <-  Neo4j scores, evidence
```

Graph model: every node is `(:Entity {key, type, label})` with type PERSON, PHONE, VEHICLE, ACCOUNT or LOCATION.
Every link is `(:Entity)-[:LINKED {kind, weight, evidence}]->(:Entity)` with kind OWNS, CALLED, TRANSFERRED, LOCATED_AT or MENTIONED_WITH.

Score = 30% degree + 30% betweenness + 20% closeness + 20% eigenvector influence, each scaled 0 to 1, shown as 0 to 100.
Every score comes with a plain-language evidence sentence and the list of direct links with their source (for example "FIR 103").

## 5. CSV formats

Column names are not case-sensitive. Bad rows are skipped and listed with their line number.

| File | Required columns | Optional columns |
|---|---|---|
| FIR records | firId, accused | date, district, phone, vehicle, account, narrative |
| Call records | callerPhone, receiverPhone | callCount, lastCallDate |
| Bank transactions | fromAccount, toAccount | amount, date |

Uploading the same file twice does not double the weights.

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| Error page saying the graph database is not reachable | Start the Neo4j database in Neo4j Desktop and check the password in `application.properties`. |
| `release version 17 not supported` | Eclipse is using an older JDK. Set JDK 17 in Preferences > Installed JREs and in Project > Properties > Java Compiler. |
| Maven shows red errors after import | Right-click the project > Maven > Update Project, tick Force Update. |
| Port 8080 already in use | Change `server.port` in `application.properties`. |
| Forgot demo passwords or want a clean start | Stop the app and delete the `data` folder in the project root. Users are recreated on the next start. |
| Want to wipe the graph | In Neo4j Browser: `MATCH (n:Entity) DETACH DELETE n` |

## 7. Honest notes about this build

- **Neo4j access** uses the official Neo4j Java driver with Cypher (the same driver Spring Data Neo4j uses underneath), not Spring Data Neo4j `@Node` classes. This avoids object-mapping surprises with cyclic graphs. All graph queries are in `GraphQueryService`, `GraphWriter`, `IngestionService` and `AnalysisService`.
- **Entity extraction** uses regular expressions for phones, plates and accounts, and a title pattern ("Mr.", "Shri", "Smt") for names in narratives. It does not use a trained NLP model, so a name with no title in free text is not found. Structured columns (accused, phone, vehicle, account) are always used.
- **Name merging** uses Jaro-Winkler similarity (threshold 0.94 in `NameMatcher`) plus an initial rule. Two different people with very similar names can be merged by mistake, so review the merge notes shown after each upload.
- **Communities** use label propagation written in `AnalysisService` with a fixed order, so results are identical on every run.
- **Eigenvector centrality** can fail to converge on some graph shapes. The code then falls back to PageRank and says so in the confirmation message and the audit log.
- **Places** (districts) are drawn on the graph but not scored, because living in the same district is not a real link between two people.
- Demo passwords are for development only.

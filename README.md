# TraceX

**Criminal network analysis with explainable ranking and human review.**

Police investigations keep data in many places: FIR records, call records and bank transfers. Hidden links between people, phones, vehicles and accounts are hard to find by hand. TraceX joins this data into one graph, shows who is most important in the network, and explains why.

<!-- Add a screenshot of the Graph page here: drag and drop the image while editing -->

## What it does

- Upload FIR, call record and bank transaction files (CSV)
- Finds phones, vehicles, accounts, people and places inside FIR text
- Merges different spellings of one person (for example "R. Patil" and "Rahul Patil")
- Builds a graph of people, phones, vehicles, accounts and places in Neo4j
- Ranks key people with a score from 0 to 100
- Gives a plain-language reason and the source record (for example "FIR 103") for every score
- Shows an interactive network graph and a "Find a link" search between two people
- An officer accepts or rejects every flag. The system never acts on its own
- Role-based login and a full audit log of every action

## Why it is different

| Idea | What it means |
|---|---|
| Network first | Looks at the whole group, not only one suspect |
| Explainable | Every score comes with evidence from real records |
| Human in control | Accept or reject buttons, nothing happens automatically |
| Auditable | Every upload, analysis and decision is logged |
| Free tools only | Open-source stack, no license fee |

## Tech stack

| Part | Technology |
|---|---|
| Language | Java 17 |
| Web | Spring Boot 3.3, Thymeleaf, HTML, CSS, JavaScript |
| Graph database | Neo4j |
| Users, audit log, decisions | H2 (file-based) with Spring Data JPA |
| Graph algorithms | JGraphT |
| CSV reading | OpenCSV |
| Name matching | Apache Commons Text (Jaro-Winkler) |
| Login and roles | Spring Security |
| AI name finding (optional) | Python with spaCy (`en_core_web_lg`) |

## How it works

```
Upload (CSV) -> IngestionService -> EntityExtractor + EntityResolver -> GraphBatch -> GraphWriter -> Neo4j
                      |                                                                                |
                 AuditService                                                              AnalysisService (JGraphT)
                                                                                                       |
Browser <- Thymeleaf pages <- Controllers <- GraphQueryService / ReviewService <- Neo4j scores and evidence
```

**Graph model**

- Every node is an `Entity` with a type: `PERSON`, `PHONE`, `VEHICLE`, `ACCOUNT` or `LOCATION`
- Every link is `LINKED` with a kind: `OWNS`, `CALLED`, `TRANSFERRED`, `LOCATED_AT` or `MENTIONED_WITH`

**Score**

```
Score = 30% degree + 30% betweenness + 20% closeness + 20% eigenvector influence
```

Each part is scaled from 0 to 1, then the total is shown from 0 to 100. Places are drawn on the graph but not scored, because living in the same district is not a real link between two people.

**Groups (communities)** are found with label propagation using a fixed order, so the result is the same on every run.

## Getting started

### 1. Install

- JDK 17 (Temurin or any OpenJDK 17+)
- Neo4j Desktop (or a free Neo4j Aura instance). Create a database, set a password and start it. Default address: `bolt://localhost:7687`, user `neo4j`
- Eclipse IDE for Enterprise Java and Web Developers (or any IDE that supports Maven)

### 2. Run

1. Clone this repository
2. Import it as a Maven project (Eclipse: File > Import > Maven > Existing Maven Projects)
3. Open `src/main/resources/application.properties` and set `spring.neo4j.authentication.password` to your Neo4j password
4. Run `TraceXApplication.java` as a Java Application
5. Open <http://localhost:8080> and sign in

> Never commit your real Neo4j password. Keep `changeme` in the repository and change it only on your own computer.

### 3. Demo accounts (development only)

| Username | Password | What this role can do |
|---|---|---|
| analyst | analyst123 | Upload, run analysis, accept or reject flags |
| supervisor | super123 | Everything an analyst can, plus the audit log |
| auditor | audit123 | Read-only results, plus the audit log |

Change these in `DataInitializer.java` before any real use.

## Try the whole flow in two minutes

1. Sign in as `analyst`. Go to **Upload** and click **Load sample data**. You get three reports (7 FIR rows, 6 call rows, 5 transfers) and a note that "R. Patil" was merged into "Rahul Patil".
2. On the dashboard click **Run analysis**.
3. Open **Key entities** to see ranked people. Open one to read the evidence, then click **Accept** or **Reject**.
4. Open **Graph** to see the network. Node size shows the score. Double-click a node to see its details.
5. Open **Find a link** and pick two people to see the chain between them.
6. Sign out, sign in as `auditor` and open **Audit log** to see every step you just did.

The sample case has planted links you can check:

- One person written two ways (R. Patil / Rahul Patil)
- One shared phone
- Two people sharing a vehicle
- A middleman (Suresh Kale) whom four people call
- An unrelated case (Deepak Pawar) that stays in its own group

## CSV formats

Column names are not case-sensitive. Bad rows are skipped and listed with their line number. Uploading the same file twice does not double the weights.

| File | Required columns | Optional columns |
|---|---|---|
| FIR records | firId, accused | date, district, phone, vehicle, account, narrative |
| Call records | callerPhone, receiverPhone | callCount, lastCallDate |
| Bank transactions | fromAccount, toAccount | amount, date |

Sample files are in `src/main/resources/sample/`.

## AI entity extraction (optional)

TraceX can use a spaCy model to read free-text FIR narratives. This runs as a small separate Python service on your own computer. No text is sent to the internet.

- **Without the service:** TraceX uses pattern matching only. Names are found only after a title such as "Mr." or "Shri".
- **With the service:** the AI also finds names and places written without a title, for example "Rahul Patil was seen meeting Nitin Bhosale near Hinjewadi". Pattern results are always kept, so the AI can only add findings.
- The Upload page shows whether the AI is ON or OFF.
- Every link made from a narrative records its source in the evidence: `(pattern)`, `(spaCy)` or `(spaCy + pattern)`.
- Settings are in `application.properties`: `tracex.nlp.enabled` and `tracex.nlp.url`.

> The Python service is not included in this repository. TraceX works fully without it.

## Security

- Login with three roles: analyst, supervisor, auditor
- Analysts and supervisors can upload, run analysis and review. Auditors and supervisors can open the audit log
- Every upload, analysis run, accept and reject is written to the audit log
- Everything runs on your own server. No outside services are needed

## Run the tests

```
mvn test
```

The tests check phone, plate and account cleaning, name matching, entity extraction, name merging, and the fallback when the AI service is off or unreachable.

## Known limits

- **Entity extraction** uses pattern rules plus an optional general-purpose English model. It can miss some names or label a person as an organisation. Structured columns (accused, phone, vehicle, account) are always used.
- **Name merging** uses Jaro-Winkler similarity (threshold 0.94) plus an initial rule. Two different people with very similar names can be merged by mistake, so check the merge notes shown after each upload.
- **Eigenvector centrality** can fail to converge on some graph shapes. TraceX then uses PageRank and says so in the confirmation message and the audit log.
- Demo passwords are for development only.

## Troubleshooting

| Problem | Fix |
|---|---|
| Error page saying the graph database is not reachable | Start the Neo4j database and check the password in `application.properties` |
| `release version 17 not supported` | Set JDK 17 in Eclipse: Preferences > Java > Installed JREs, and Project > Properties > Java Compiler |
| Maven shows red errors after import | Right-click the project > Maven > Update Project, tick Force Update |
| AI status says OFF | Start the Python service, or ignore it. TraceX works without it |
| Port 8080 already in use | Change `server.port` in `application.properties` |
| Forgot demo passwords or want a clean start | Stop the app and delete the `data` folder. Users are recreated on the next start |
| Want to wipe the graph | In Neo4j Browser run `MATCH (n:Entity) DETACH DELETE n` |

## Project info

Built by Team TraceX for Smart India Hackathon 2026
Problem Statement: SIH26189, AI-Powered Criminal Network Analysis System

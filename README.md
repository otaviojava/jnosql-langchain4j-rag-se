# jnosql-langchain4j-rag-se

A Java SE example of Retrieval-Augmented Generation (RAG) that combines
**LangChain4j**, **Eclipse JNoSQL**, and **Qdrant** as the single database.
It demonstrates versioned, domain-aware retrieval: semantic search finds candidate
policies, and domain rules decide which of them apply.

> All policies, deadlines and dates in this project are **fictional examples**, not real
> financial regulations.

**Central lesson:** semantic relevance identifies candidate knowledge, while domain
rules determine which knowledge is applicable.

## Why Two-Stage Retrieval?

Traditional RAG relies on semantic similarity to find knowledge. Similarity does not
guarantee applicability: two revisions of a financial policy can both be semantically
relevant while only one applies to the customer's business date.

Two-Stage Retrieval separates two responsibilities. **Stage 1, Semantic Retrieval,**
uses embeddings and vector similarity to identify candidate policies. **Stage 2,
Business Retrieval,** loads the authoritative `Policy` entities by ID and validates
domain rules, namely the effective period, before anything reaches the model.

Only applicable policies become context, and the language model answers from that
validated context. **Semantic relevance finds candidates. Business rules establish
applicability. Only validated context reaches the language model.**

## Architecture

The application is a single Java SE process (Weld SE, no web server). Eclipse JNoSQL
(`VectorTemplate` in Demo 1, a Jakarta Data repository in Demo 2) persists and loads the
`Policy` record, and LangChain4j provides the local embedding model, the Qdrant
`EmbeddingStore` and the `PolicyAssistant` AI service. Qdrant is the only database.

### System Context — C4 Level 1

```mermaid
C4Context
    title System Context - Two-Stage Retrieval
    Person(user, "Employee / Application User", "Submits a question and an explicit business date")
    System(app, "Two-Stage Retrieval Application", "Java SE: discovers policies, validates applicability, generates grounded answers. Embeds locally.")
    System_Ext(qdrant, "Qdrant", "Policy entities, metadata and vector embeddings")
    System_Ext(openai, "OpenAI API", "Chat model for structured answers")
    Rel(user, app, "Asks question + business date")
    BiRel(app, qdrant, "Searches vectors, reads and writes policies", "gRPC")
    Rel(app, openai, "Sends validated context + question", "HTTPS")
```

The system boundary is one command-line Java SE application. It generates embeddings
itself with a local model (all-MiniLM-L6-v2); OpenAI is used only for chat generation.
Qdrant and OpenAI are infrastructure dependencies, not business actors.

### Runtime Architecture — C4 Level 2

Packages and frameworks are logical components inside one process, not separate services.

```mermaid
flowchart TB
    user(["Employee / Application User"])

    subgraph jvm["Java SE Application (Weld SE + MicroProfile Config)"]
        entry["Entry Point<br/>App (Demo 1) / DataApp (Demo 2)"]
        assistant["Policy Assistant<br/>LangChain4j AI Service"]
        subgraph retrieval["Two-Stage Retrieval"]
            semantic["Stage 1: Semantic Retrieval<br/>local embedding model + EmbeddingStore"]
            business["Stage 2: Business Retrieval<br/>PolicyRetriever"]
        end
        subgraph persistence["Persistence (one variant per demo)"]
            vt["Demo 1: VectorTemplate"]
            repo["Demo 2: Jakarta Data PolicyRepository"]
        end
        domain["Domain Model<br/>Policy.appliesOn(LocalDate)"]
    end

    subgraph infra["External infrastructure"]
        qdrant[("Qdrant<br/>collection: policies")]
        openai["OpenAI API<br/>chat model"]
    end

    user --> entry
    entry --> assistant
    assistant --> semantic
    semantic --> business
    business --> vt
    business --> repo
    business --> domain
    semantic -->|"vector search"| qdrant
    vt -->|"entities by ID"| qdrant
    repo -->|"entities by ID"| qdrant
    assistant -->|"validated context"| openai
```

Semantic search (LangChain4j) and entity retrieval (JNoSQL) use the **same Qdrant
collection**, `policies`. Jakarta Data does not perform vector similarity search in
JNoSQL 1.2.0; it only loads entities by ID, so both demos share the same semantic stage.

### Two-Stage Retrieval — Sequence Diagram

```mermaid
sequenceDiagram
    actor U as Employee / Caller
    participant A as Java SE Application
    participant PA as PolicyAssistant
    participant PR as PolicyRetriever
    participant EM as Local Embedding Model
    participant ES as LangChain4j EmbeddingStore
    participant Q as Qdrant
    participant P as JNoSQL Template / Jakarta Data Repository
    participant D as Policy Domain Rules
    participant L as OpenAI Chat Model

    U->>A: question + business date (LocalDate)
    A->>PA: analyze(question, InvocationParameters{businessDate})
    Note over A,PA: The date is typed input, never parsed from the question
    PA->>PR: retrieve(Query with invocation parameters)

    rect rgb(235, 245, 255)
    Note over PR,Q: SEMANTIC RELEVANCE - finds candidates only
    PR->>EM: embed(question)
    EM-->>PR: query embedding
    PR->>ES: search(embedding, maxResults)
    ES->>Q: vector similarity search
    Q-->>ES: nearest points
    ES-->>PR: candidate policy IDs
    end

    rect rgb(255, 245, 230)
    Note over PR,D: BUSINESS APPLICABILITY - decides what is valid
    alt Demo 1
        PR->>P: VectorTemplate.find(Policy.class, id)
    else Demo 2
        PR->>P: PolicyRepository.findByIdIn(ids)
    end
    P->>Q: load entities by ID
    Q-->>P: Policy entities
    P-->>PR: candidate Policy entities
    PR->>D: policy.appliesOn(businessDate)
    D-->>PR: applicable / not applicable
    Note over PR: Inapplicable revisions are excluded
    end

    PR-->>PA: formatted context (applicable policies only)

    alt applicable policies found
        PA->>L: system message + validated context + question
        L-->>PA: structured PolicyResponse
    else no applicable policy
        PA->>L: system message + question, no policy context
        Note over L: Prompt forbids inventing deadlines: states the gap and sets requiresHumanReview = true
        L-->>PA: PolicyResponse (human review required)
    end
    PA-->>A: PolicyResponse
    A-->>U: answer, recommendations, human review flag
```

Ingestion uses the same `EmbeddingModel` bean, but runs separately at startup:

```mermaid
sequenceDiagram
    participant PL as PolicyLoader
    participant J as policies.json (classpath)
    participant E as Policy (record)
    participant EM as Local Embedding Model
    participant P as JNoSQL Template / Jakarta Data Repository
    participant Q as Qdrant

    PL->>J: PolicyDefinition.readAll
    loop each revision (own UUID)
        PL->>P: exists by id?
        alt already stored
            Note over PL: skipped, never overwritten
        else missing
            PL->>EM: embed(title + content)
            PL->>E: toPolicy(DenseVector)
            Note over E: policyCode shared by revisions,<br/>open end stored as 9999-12-31
            PL->>P: insert (forStorage)
            P->>Q: upsert point + payload
        end
    end
```

### Architectural Decisions and Trade-offs

| Decision | Rationale |
|---|---|
| Two-stage retrieval | Keeps semantic similarity (candidates) separate from authoritative domain validation (applicability). |
| One Qdrant database | The same `policies` collection holds vectors and entity payloads, avoiding extra infrastructure in this demo. |
| Jakarta Data and Eclipse JNoSQL | Familiar Java abstractions to persist and load domain entities; Demo 1 uses `VectorTemplate`, Demo 2 a repository. |
| Java SE | Keeps the focus on persistence and retrieval instead of HTTP infrastructure. |
| Immutable policy revisions | Preserves historical applicability; published definitions are never silently changed. |
| Explicit business dates | Makes temporal validation deterministic, independent of natural-language date extraction. |
| Limitations | A fixed candidate count (`rag.search.max-results=10`) suits this small dataset. Production needs candidate recall, access control and database-side filtering. |

## Requirements

Java 21, Maven, Docker, and an OpenAI API key (only to run the application).

## Start Qdrant

```bash
docker compose up -d     # gRPC on 6334, HTTP on 6333
docker compose down      # stop
```

## Configure the API key

The key is read from the `RAG_CHAT_API_KEY` environment variable, which
MicroProfile Config resolves as `rag.chat.api-key`. It is never stored in
`microprofile-config.properties`.

```bash
cp .env.example .env
# edit .env and replace the placeholder with your key
```

`.env` is git-ignored; only `.env.example` is tracked. Java does not load `.env`
files by itself, so `scripts/run.sh` sources it with `set -a`. **`.env` is executed
as shell code: treat it as trusted local configuration and never put untrusted
commands in it.**

**IntelliJ IDEA:** open *Run | Edit Configurations*, select the `App` configuration,
and add `RAG_CHAT_API_KEY=<your key>` under *Environment variables*.

## Reset the development collection

The collection must be recreated once after upgrading from the earlier, unversioned
sample data (existing ids are skipped, never updated). This is a destructive,
development-only step that is never run by the application, tests or build:

```bash
./scripts/reset-qdrant.sh    # asks for confirmation; uses QDRANT_URL (default http://localhost:6333)
```

It deletes only the `policies` collection and recreates it (384 dimensions, cosine).
Other collections and the Docker volume are untouched. The next application start
inserts the four policy records.

## Run the application

```bash
./scripts/run.sh            # Demo 1: Eclipse JNoSQL VectorTemplate (default)
./scripts/run.sh data       # Demo 2: Jakarta Data repository
```

The script validates the key and runs `mvn compile exec:java` (main class chosen via `-Ddemo.main`), preserving its exit code.

## Two demos, one entity

Both demos run the same three scenarios against the same `policies` collection. LangChain4j does semantic discovery in both (Jakarta Data repositories in JNoSQL 1.2.0 have no vector similarity query); they differ in how entities are persisted and fetched.

| Package | Demo 1 – `template` | Demo 2 – `data` |
|---|---|---|
| Entry point | `app.App` | `app.DataApp` |
| Persistence | `VectorTemplate` | `PolicyRepository` (`@Repository`, `CrudRepository<Policy, UUID>`) |
| Existence check / fetch | `VectorTemplate.find` | derived `existsById` / `findByIdIn` |

Packages: `model` (the shared `Policy` entity), `template` and `data` (loader, retriever, assistant per demo), `shared` (prompts, response record, JSON definitions, scenarios, context formatting), `infra` (LangChain4j CDI producers, date converter), `app` (entry points).

## Run tests

```bash
mvn test      # unit tests, no external services
mvn verify    # also runs Qdrant integration tests (needs Docker, no OpenAI key)
```

The integration tests start their own Qdrant Testcontainer and use a stub chat model,
so no OpenAI key is needed. They verify persistence through JNoSQL, that LangChain4j
and JNoSQL share the `policies` collection, the revision selected per business date,
and that only applicable policies reach the model prompt. Live LLM output is not
asserted because it is non-deterministic.

## Policy revisions and temporal applicability

- Policies are **immutable revisions**. Each revision has its own UUID (`id`); the stable
  business identity shared by revisions is `policyCode` (e.g. `CARD-DISPUTE`).
- A revision applies from `effectiveFrom` (inclusive) until `effectiveUntil` (exclusive);
  a null end means open-ended. `Policy.appliesOn(LocalDate)` implements this.
- `policies.json` has four records: Card Dispute v1.0 (30 days, until 2026-07-01), Card
  Dispute v2.0 (60 days, from 2026-07-01), Fraud Investigation and Identity Verification.
- Embeddings are generated from title and content only, never stored in JSON.
- `PolicyLoader` inserts a record only if its UUID is missing and never updates existing
  ones. To change a published policy, add a new revision with a new UUID.
- Storage note: JNoSQL writes null fields as null payload values, which LangChain4j's
  Qdrant store cannot read. An open-ended period is therefore persisted with the
  `9999-12-31` sentinel (`OpenEndedDateConverter`) and read back as `null`.

## How the business date reaches the retriever

The date is explicit, typed input. `PolicyAssistant.analyze(question, InvocationParameters)`
receives a LangChain4j `InvocationParameters` carrying a `LocalDate` under `businessDate`;
LangChain4j exposes it to `PolicyRetriever` as `query.metadata().invocationParameters()`.
Dates are never parsed from the question text, and a missing date is an error.

For this small demo the candidate count (`rag.search.max-results=10`) covers all
revisions. Production systems should consider database-side filters, authorization and
candidate recall, and should not treat similarity scores as probabilities.

## Demonstration scenarios

`./scripts/run.sh` runs three scenarios and prints the question, business date, answer,
human-review flag and recommendations:

1. **Historical policy** (2026-05-15): a report 20 days after a May 2026 purchase uses
   Card Dispute v1.0 (30 days).
2. **Current policy** (2026-08-15): a report 45 days after an August 2026 purchase uses
   v2.0 (60 days), so it is within the period.
3. **Insufficient knowledge**: an international wire-transfer reversal is not covered by
   any policy. Similar card policies may be retrieved, but similarity is not evidence;
   the assistant must say so, require human review and not invent a deadline.

# jnosql-langchain4j-rag-se

A Java SE example of Retrieval-Augmented Generation (RAG) that combines
**LangChain4j**, **Eclipse JNoSQL**, and **Qdrant** as the single database.
It demonstrates a question that the policies can answer and one they cannot.

> All policies in this project are **fictional** and exist for demonstration only.

## Architecture

- **Eclipse JNoSQL** (`VectorTemplate`) owns ingestion and entity lookup. The
  `Policy` record is mapped with `@Entity("policies")`.
- **LangChain4j** generates embeddings (local all-MiniLM-L6-v2, 384 dimensions),
  searches Qdrant (`EmbeddingStore`), and exposes `PolicyAssistant` as a CDI AI service.
- **Qdrant** stores the policies and vectors. LangChain4j searches the same
  collection (`jnosql.vector.database=policies`) that JNoSQL writes to; it returns
  point IDs, and `PolicyRetriever` loads the matching `Policy` entities through JNoSQL.
- **OpenAI** (`gpt-4o-mini`) generates the structured `PolicyResponse`.
- **Weld SE** and **MicroProfile Config** provide CDI and configuration. No web server.

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

## Run the application

```bash
./scripts/run.sh
```

The script validates the key and runs `mvn compile exec:java`, preserving its exit code.

## Run tests

```bash
mvn test      # unit tests, no external services
mvn verify    # also runs the Qdrant integration test (needs Docker, no OpenAI key)
```

The integration test starts its own Qdrant Testcontainer, ingests the policies through
JNoSQL, checks that LangChain4j returns matching point IDs, that JNoSQL resolves them,
and that `PolicyRetriever` returns the 30-day dispute policy. Live LLM scenarios are
not part of the automated build because LLM output is non-deterministic.

## Demonstration scenarios

1. **Supported:** an unauthorized card transaction reported 20 days after purchase.
   The Card Transaction Dispute policy (30-day window) applies, so the assistant gives
   grounded recommendations and human review is normally not required.
2. **Unsupported:** reversing an international wire transfer after 45 days. No sample
   policy covers wire-transfer reversals. Vector search still returns the closest
   policies, but similarity is not evidence: the system message instructs the model to
   verify that a policy explicitly answers the question, otherwise to say the information
   is insufficient, set `requiresHumanReview` to true, and recommend escalation without
   inventing deadlines or procedures.

## Sample policies and idempotent ingestion

Policies live in `src/main/resources/policies.json` (without embeddings) and are loaded
from the classpath. `PolicyLoader` generates embeddings with LangChain4j and inserts each
policy through JNoSQL, but only if its stable UUID does not already exist. Existing policies
are skipped, never updated, and their embeddings are not regenerated.

Editing an already-inserted policy in the JSON therefore does **not** change stored data.
To apply such a change, recreate the Qdrant collection (e.g. `docker compose down -v`)
or publish a new policy revision with a new id.

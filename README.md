# jnosql-langchain4j-rag-se

A Java SE foundation for a future enterprise RAG application with LangChain4j,
Eclipse JNoSQL, and Qdrant. This initial project demonstrates CDI, configuration,
and vector persistence; it does not yet implement LangChain4j, embeddings, or RAG.

## Technology stack

- Java 21 and Maven
- Eclipse JNoSQL 1.2.0 vector mapping and Qdrant driver
- Weld SE and SmallRye MicroProfile Config
- JUnit Jupiter, AssertJ, Mockito, and Testcontainers

## Requirements

Install Java 21, Maven, and Docker.

## Start Qdrant locally

```bash
docker compose up -d
docker compose down
```

The Compose service exposes Qdrant's HTTP API on port 6333 and gRPC API on
port 6334, and stores its data in a named volume.

## Run the application

```bash
mvn compile exec:java
```

Weld starts as a Java SE container, injects JNoSQL's `VectorTemplate`, and is
closed when the application exits. MicroProfile Config reads
`META-INF/microprofile-config.properties`; system properties and environment
variables can override these defaults.

## Run tests

```bash
mvn test
mvn verify
```

`mvn test` runs the CDI and configuration smoke test. `mvn verify` also runs
the Qdrant integration test, which starts its own Qdrant Testcontainer and
persists/retrieves a policy and its three-dimensional vector. It does not
depend on the Compose service.

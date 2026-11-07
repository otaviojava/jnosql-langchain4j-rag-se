package com.github.otaviojava.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ingestion (JNoSQL) and retrieval (LangChain4j) against the same
 * Qdrant collection. Needs Docker but no OpenAI API key.
 */
@Testcontainers
class PolicyRetrievalIntegrationTest {

    private static final UUID CARD_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @Container
    static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.14.1");

    private static WeldContainer container;

    @BeforeAll
    static void start() {
        System.setProperty("jnosql.qdrant.host", QDRANT.getHost());
        System.setProperty("jnosql.qdrant.port", String.valueOf(QDRANT.getGrpcPort()));
        System.setProperty("jnosql.vector.database", "policies");
        System.setProperty("jnosql.qdrant.collection.vector.size", "384");
        System.setProperty("rag.chat.api-key", "not-used-in-this-test");
        container = new Weld().initialize();
        container.select(PolicyLoader.class).get().load();
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.close();
        }
        List.of("jnosql.qdrant.host", "jnosql.qdrant.port", "jnosql.vector.database",
                "jnosql.qdrant.collection.vector.size", "rag.chat.api-key")
                .forEach(System::clearProperty);
    }

    @Test
    void shouldShareTheCollectionBetweenJNoSqlAndLangChain4j() {
        var embeddingModel = container.select(EmbeddingModel.class).get();
        var store = container.select(new jakarta.enterprise.util.TypeLiteral<EmbeddingStore<TextSegment>>() {
        }).get();
        var template = container.select(VectorTemplate.class).get();

        var matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("unauthorized credit card transaction").content())
                .maxResults(10)
                .build()).matches();

        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(m -> UUID.fromString(m.embeddingId()))
                .contains(CARD_ID)
                .allSatisfy(id -> assertThat(template.find(Policy.class, id)).isPresent());
    }

    @Test
    void shouldNotDuplicateWhenLoadedAgain() {
        container.select(PolicyLoader.class).get().load();

        var embeddingModel = container.select(EmbeddingModel.class).get();
        var store = container.select(new jakarta.enterprise.util.TypeLiteral<EmbeddingStore<TextSegment>>() {
        }).get();
        var matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("policy").content())
                .maxResults(10)
                .build()).matches();

        assertThat(matches).hasSize(3);
    }

    @Test
    void shouldRetrieveDisputePolicyForSupportedQuestion() {
        var retriever = container.select(PolicyRetriever.class).get();

        List<Content> contents = retriever.retrieve(Query.from(App.SUPPORTED_QUESTION));

        assertThat(contents).isNotEmpty();
        assertThat(contents).extracting(c -> c.textSegment().text())
                .anyMatch(text -> text.contains("Card Transaction Dispute")
                        && text.contains("30 days"));
    }
}

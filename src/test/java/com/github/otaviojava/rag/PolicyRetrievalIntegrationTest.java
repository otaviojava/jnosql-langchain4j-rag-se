package com.github.otaviojava.rag;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.query.Metadata;
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

import jakarta.enterprise.util.TypeLiteral;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises ingestion (JNoSQL) and retrieval (LangChain4j) against the same
 * Qdrant collection. Needs Docker but no OpenAI API key.
 */
@Testcontainers
class PolicyRetrievalIntegrationTest {

    private static final UUID CARD_V1_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID FRAUD_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID IDENTITY_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID CARD_V2_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

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

    private static EmbeddingStore<TextSegment> store() {
        return container.select(new TypeLiteral<EmbeddingStore<TextSegment>>() {
        }).get();
    }

    private static List<String> retrieve(String question, LocalDate date) {
        InvocationContext context = InvocationContext.builder()
                .invocationParameters(InvocationParameters.from(
                        PolicyRetriever.BUSINESS_DATE, date))
                .build();
        var query = Query.from(question, Metadata.builder()
                .chatMessage(UserMessage.from(question))
                .invocationContext(context)
                .build());
        return container.select(PolicyRetriever.class).get().retrieve(query).stream()
                .map(c -> c.textSegment().text())
                .toList();
    }

    @Test
    void shouldPersistAndRetrieveAllFieldsThroughJNoSql() {
        var template = container.select(VectorTemplate.class).get();

        Policy v2 = template.find(Policy.class, CARD_V2_ID).orElseThrow();

        assertThat(v2.id()).isEqualTo(CARD_V2_ID);
        assertThat(v2.policyCode()).isEqualTo("CARD-DISPUTE");
        assertThat(v2.version()).isEqualTo("2.0");
        assertThat(v2.effectiveFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v2.effectiveUntil()).isNull();
        assertThat(v2.content()).contains("60 days");

        Policy v1 = template.find(Policy.class, CARD_V1_ID).orElseThrow();
        assertThat(v1.effectiveUntil()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v1.content()).contains("30 days");
    }

    @Test
    void shouldStoreEmbeddingInQdrant() {
        var embeddingModel = container.select(EmbeddingModel.class).get();
        var matches = store().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("unauthorized credit card transaction").content())
                .maxResults(10)
                .build()).matches();

        assertThat(matches).extracting(m -> UUID.fromString(m.embeddingId()))
                .containsExactlyInAnyOrder(CARD_V1_ID, CARD_V2_ID, FRAUD_ID, IDENTITY_ID);
        assertThat(matches).allSatisfy(m -> assertThat(m.embedding()).isNotNull());
    }

    @Test
    void shouldNotDuplicateWhenLoadedAgain() {
        container.select(PolicyLoader.class).get().load();

        var embeddingModel = container.select(EmbeddingModel.class).get();
        var matches = store().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("policy").content())
                .maxResults(10)
                .build()).matches();

        assertThat(matches).hasSize(4);
    }

    @Test
    void shouldRetrieveHistoricalRevisionOnly() {
        var contexts = retrieve(App.HISTORICAL_QUESTION, App.HISTORICAL_DATE);

        assertThat(contexts).anyMatch(t -> t.contains("CARD-DISPUTE")
                && t.contains("Version: 1.0") && t.contains("30 days"));
        assertThat(contexts).noneMatch(t -> t.contains("Version: 2.0") || t.contains("60 days"));
    }

    @Test
    void shouldRetrieveCurrentRevisionOnly() {
        var contexts = retrieve(App.CURRENT_QUESTION, App.CURRENT_DATE);

        assertThat(contexts).anyMatch(t -> t.contains("CARD-DISPUTE")
                && t.contains("Version: 2.0") && t.contains("60 days"));
        assertThat(contexts).noneMatch(t -> t.contains("Version: 1.0") && t.contains("CARD-DISPUTE"));
    }

    @Test
    void shouldRequireBusinessDate() {
        var retriever = container.select(PolicyRetriever.class).get();

        assertThatThrownBy(() -> retriever.retrieve(Query.from("question")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PolicyRetriever.BUSINESS_DATE);
    }

    @Test
    void shouldPassApplicableContextAndDateToTheAiService() {
        RecordingChatModel.REQUESTS.clear();
        var assistant = container.select(PolicyAssistant.class).get();

        PolicyResponse response = assistant.analyze(App.HISTORICAL_QUESTION,
                InvocationParameters.from(PolicyRetriever.BUSINESS_DATE, App.HISTORICAL_DATE));

        assertThat(response.answer()).isEqualTo("stub answer");
        assertThat(response.requiresHumanReview()).isTrue();
        assertThat(response.recommendations()).containsExactly("stub recommendation");

        String prompt = RecordingChatModel.REQUESTS.getFirst().messages().toString();
        assertThat(prompt).contains("Version: 1.0").contains("30 days");
        assertThat(prompt).doesNotContain("Version: 2.0").doesNotContain("60 days");
    }
}

package com.github.otaviojava.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyLoaderTest {

    private static final UUID CARD_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    private EmbeddingModel embeddingModel;
    private VectorTemplate template;
    private PolicyLoader loader;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        template = mock(VectorTemplate.class);
        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f, 0.3f})));
        when(template.find(eq(Policy.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        loader = new PolicyLoader(embeddingModel, template);
    }

    @Test
    void shouldParseThreePoliciesFromClasspath() {
        List<PolicyLoader.PolicyDefinition> definitions =
                loader.readDefinitions(PolicyLoader.POLICIES_RESOURCE);

        assertThat(definitions).hasSize(3);
        assertThat(definitions).extracting(PolicyLoader.PolicyDefinition::title)
                .containsExactly("Card Transaction Dispute",
                        "Fraud Investigation",
                        "Customer Identity Verification");
        assertThat(definitions).extracting(PolicyLoader.PolicyDefinition::department)
                .containsExactly("Payments", "Fraud Prevention", "Compliance");
        assertThat(definitions).allSatisfy(d -> {
            assertThat(d.version()).isEqualTo("1.0");
            assertThat(d.active()).isTrue();
        });
        assertThat(definitions.getFirst().id()).isEqualTo(CARD_ID);
        assertThat(definitions.getFirst().content()).contains("30 days");
    }

    @Test
    void shouldInsertMissingPolicies() {
        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(template, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::title)
                .containsExactly("Card Transaction Dispute",
                        "Fraud Investigation",
                        "Customer Identity Verification");
        assertThat(captor.getAllValues()).allSatisfy(p ->
                assertThat(p.embedding()).isNotNull());
        verify(embeddingModel, times(3)).embed(any(String.class));
    }

    @Test
    void shouldSkipExistingPoliciesAndNotEmbedThem() {
        when(template.find(eq(Policy.class), eq(CARD_ID)))
                .thenReturn(Optional.of(mock(Policy.class)));

        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(template, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::id)
                .doesNotContain(CARD_ID);
        verify(embeddingModel, times(2)).embed(any(String.class));
    }

    @Test
    void shouldNotInsertOrEmbedWhenAllPoliciesExist() {
        when(template.find(eq(Policy.class), any(UUID.class)))
                .thenReturn(Optional.of(mock(Policy.class)));

        loader.load();
        loader.load();

        verify(template, never()).insert(any(Policy.class));
        verify(embeddingModel, never()).embed(any(String.class));
    }

    @Test
    void shouldFailClearlyWhenResourceIsMissing() {
        assertThatThrownBy(() -> loader.readDefinitions("missing-policies.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-policies.json");
    }

    @Test
    void shouldFailClearlyWhenResourceIsMalformed() {
        assertThatThrownBy(() -> loader.readDefinitions("META-INF/beans.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed policy resource");
    }
}

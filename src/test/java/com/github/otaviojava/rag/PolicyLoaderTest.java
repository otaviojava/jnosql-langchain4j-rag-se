package com.github.otaviojava.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
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
    void shouldParseVersionedPoliciesFromClasspath() {
        List<PolicyLoader.PolicyDefinition> definitions =
                loader.readDefinitions(PolicyLoader.POLICIES_RESOURCE);

        assertThat(definitions).hasSize(4);
        assertThat(definitions).extracting(PolicyLoader.PolicyDefinition::id)
                .doesNotHaveDuplicates();
        assertThat(definitions).extracting(d -> d.policyCode() + " v" + d.version())
                .containsExactly("CARD-DISPUTE v1.0", "CARD-DISPUTE v2.0",
                        "FRAUD-INVESTIGATION v1.0", "IDENTITY-VERIFICATION v1.0");

        var v1 = definitions.get(0);
        assertThat(v1.id()).isEqualTo(CARD_ID);
        assertThat(v1.from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(v1.until()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v1.content()).contains("30 days");

        var v2 = definitions.get(1);
        assertThat(v2.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v2.until()).isNull();
        assertThat(v2.content()).contains("60 days");
    }

    @Test
    void shouldInsertMissingPolicies() {
        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(template, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::policyCode)
                .containsExactly("CARD-DISPUTE", "CARD-DISPUTE",
                        "FRAUD-INVESTIGATION", "IDENTITY-VERIFICATION");
        assertThat(captor.getAllValues()).allSatisfy(p ->
                assertThat(p.embedding()).isNotNull());
        verify(embeddingModel, times(4)).embed(any(String.class));
    }

    @Test
    void shouldSkipExistingPoliciesAndNotEmbedThem() {
        when(template.find(eq(Policy.class), eq(CARD_ID)))
                .thenReturn(Optional.of(mock(Policy.class)));

        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(template, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::id)
                .doesNotContain(CARD_ID);
        verify(embeddingModel, times(3)).embed(any(String.class));
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
    void shouldEmbedOnlyTitleAndContent() {
        loader.load();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(embeddingModel, times(4)).embed(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(text ->
                assertThat(text).doesNotContain("550e8400").doesNotContain("2025-01-01"));
    }

    @Test
    void shouldFailClearlyWhenResourceIsMalformed() {
        assertThatThrownBy(() -> loader.readDefinitions("META-INF/beans.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed policy resource");
    }
}

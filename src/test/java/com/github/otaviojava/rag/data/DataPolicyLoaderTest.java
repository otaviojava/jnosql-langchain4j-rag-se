package com.github.otaviojava.rag.data;

import com.github.otaviojava.rag.model.Policy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataPolicyLoaderTest {

    private static final UUID CARD_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    private EmbeddingModel embeddingModel;
    private PolicyRepository repository;
    private PolicyLoader loader;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        repository = mock(PolicyRepository.class);
        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f, 0.3f})));
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        loader = new PolicyLoader(embeddingModel, repository);
    }

    @Test
    void shouldInsertMissingPolicies() {
        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(repository, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::policyCode)
                .containsExactly("CARD-DISPUTE", "CARD-DISPUTE",
                        "FRAUD-INVESTIGATION", "IDENTITY-VERIFICATION");
        assertThat(captor.getAllValues()).allSatisfy(p ->
                assertThat(p.embedding()).isNotNull());
        verify(embeddingModel, times(4)).embed(any(String.class));
    }

    @Test
    void shouldSkipExistingPoliciesAndNotEmbedThem() {
        when(repository.existsById(CARD_ID)).thenReturn(true);

        loader.load();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(repository, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Policy::id)
                .doesNotContain(CARD_ID);
        verify(embeddingModel, times(3)).embed(any(String.class));
    }

    @Test
    void shouldNotInsertOrEmbedWhenAllPoliciesExist() {
        when(repository.existsById(any(UUID.class))).thenReturn(true);

        loader.load();
        loader.load();

        verify(repository, never()).insert(any(Policy.class));
        verify(embeddingModel, never()).embed(any(String.class));
    }
}

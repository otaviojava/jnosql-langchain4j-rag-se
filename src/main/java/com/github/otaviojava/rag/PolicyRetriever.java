
package com.github.otaviojava.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Named("policyRetriever")
@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    @Inject
    private EmbeddingModel embeddingModel;

    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    private VectorTemplate template;

    @Inject
    @ConfigProperty(name = "rag.search.max-results",
            defaultValue = "5")
    private int maxResults;

    @Override
    public List<Content> retrieve(Query query) {

        var embedding = embeddingModel
                .embed(query.text())
                .content();

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(maxResults)
                .build();

        return embeddingStore.search(request)
                .matches()
                .stream()
                .map(EmbeddingMatch::embeddingId)
                .map(UUID::fromString)
                .map(id -> template.find(Policy.class, id))
                .flatMap(Optional::stream)
                .filter(Policy::active)
                .map(this::toContent)
                .toList();
    }

    private Content toContent(Policy policy) {
        String text = """
                Policy: %s
                Version: %s
                Department: %s

                %s
                """.formatted(
                policy.title(),
                policy.version(),
                policy.department(),
                policy.content()
        );

        return Content.from(TextSegment.from(text));
    }
}

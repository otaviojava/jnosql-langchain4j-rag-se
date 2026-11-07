
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
import java.util.logging.Logger;

@Named("policyRetriever")
@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    private static final Logger LOGGER =
            Logger.getLogger(PolicyRetriever.class.getName());

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

        LOGGER.info(() -> "Searching policies for: " + query.text());

        var embedding = embeddingModel.embed(query.text()).content();

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(maxResults)
                .build();

        var matches = embeddingStore.search(request).matches();

        LOGGER.info(() -> "Qdrant matches: " + matches.size());

        var contents = matches.stream()
                .peek(match -> LOGGER.info(() ->
                        "Match ID: " + match.embeddingId()
                                + ", score: " + match.score()))
                .map(EmbeddingMatch::embeddingId)
                .map(UUID::fromString)
                .map(id -> {
                    var policy = template.find(Policy.class, id);

                    LOGGER.info(() ->
                            "JNoSQL lookup: " + id
                                    + ", found: " + policy.isPresent());

                    return policy;
                })
                .flatMap(Optional::stream)
                .filter(policy -> {
                    LOGGER.info(() ->
                            "Policy: " + policy.title()
                                    + ", active: " + policy.active());

                    return policy.active();
                })
                .map(this::toContent)
                .toList();

        LOGGER.info(() ->
                "RAG content count: " + contents.size());

        return contents;
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


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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Named("policyRetriever")
@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    /** Invocation parameter carrying the date on which policies must apply. */
    public static final String BUSINESS_DATE = "businessDate";

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
        LocalDate businessDate = businessDate(query);

        LOGGER.info(() -> "Searching policies applicable on " + businessDate);

        return semanticCandidates(query).stream()
                .map(id -> template.find(Policy.class, id))
                .flatMap(Optional::stream)
                .filter(policy -> {
                    boolean applies = policy.appliesOn(businessDate);
                    LOGGER.info(() -> "Policy " + policy.policyCode()
                            + " v" + policy.version()
                            + (applies ? " applies" : " excluded")
                            + " on " + businessDate);
                    return applies;
                })
                .map(PolicyRetriever::toContent)
                .toList();
    }

    /** Stage 1: semantic discovery in Qdrant; keeps relevance order. */
    private List<UUID> semanticCandidates(Query query) {
        var embedding = embeddingModel.embed(query.text()).content();

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(maxResults)
                .build();

        var matches = embeddingStore.search(request).matches();

        LOGGER.info(() -> "Qdrant candidates: " + matches.size());

        return matches.stream()
                .map(EmbeddingMatch::embeddingId)
                .map(UUID::fromString)
                .toList();
    }

    private static LocalDate businessDate(Query query) {
        var parameters = query.metadata() == null
                ? null : query.metadata().invocationParameters();
        Object date = parameters == null
                ? null : parameters.get(BUSINESS_DATE);
        if (date instanceof LocalDate localDate) {
            return localDate;
        }
        throw new IllegalArgumentException(
                "A LocalDate invocation parameter named '" + BUSINESS_DATE
                        + "' is required");
    }

    static Content toContent(Policy policy) {
        String effective = policy.effectiveUntil() == null
                ? policy.effectiveFrom() + " onwards"
                : policy.effectiveFrom() + " until "
                + policy.effectiveUntil() + " (exclusive)";

        String text = """
                Policy code: %s
                Title: %s
                Version: %s
                Effective period: %s
                Department: %s

                %s
                """.formatted(
                policy.policyCode(),
                policy.title(),
                policy.version(),
                effective,
                policy.department(),
                policy.content()
        );

        return Content.from(TextSegment.from(text));
    }
}

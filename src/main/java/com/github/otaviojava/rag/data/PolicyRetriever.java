package com.github.otaviojava.rag.data;

import com.github.otaviojava.rag.model.Policy;
import com.github.otaviojava.rag.shared.PolicyContext;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Demo 2 retriever. Stage 1: LangChain4j semantic search in Qdrant.
 * Stage 2: the Jakarta Data {@link PolicyRepository} loads the entities and domain
 * rules keep only the policies applicable on the business date.
 */
@Named("dataPolicyRetriever")
@ApplicationScoped
public class PolicyRetriever implements ContentRetriever {

    private static final Logger LOGGER =
            Logger.getLogger(PolicyRetriever.class.getName());

    @Inject
    private EmbeddingModel embeddingModel;

    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    private PolicyRepository repository;

    @Inject
    @ConfigProperty(name = "rag.search.max-results",
            defaultValue = "5")
    private int maxResults;

    @Override
    public List<Content> retrieve(Query query) {
        LocalDate businessDate = PolicyContext.businessDate(query);

        LOGGER.info(() -> "[data] Searching policies applicable on " + businessDate);

        List<UUID> candidates = semanticCandidates(query);
        Map<UUID, Policy> policies = repository.findByIdIn(candidates).stream()
                .collect(Collectors.toMap(Policy::id, Function.identity()));

        // The repository does not preserve order: restore semantic relevance order.
        return candidates.stream()
                .map(policies::get)
                .filter(policy -> policy != null && applies(policy, businessDate))
                .map(PolicyContext::toContent)
                .toList();
    }

    private static boolean applies(Policy policy, LocalDate businessDate) {
        boolean applies = policy.appliesOn(businessDate);
        LOGGER.info(() -> "Policy " + policy.policyCode()
                + " v" + policy.version()
                + (applies ? " applies" : " excluded")
                + " on " + businessDate);
        return applies;
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
}

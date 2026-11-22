package com.github.otaviojava.rag.data;

import com.github.otaviojava.rag.model.Policy;
import com.github.otaviojava.rag.shared.PolicyDefinition;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.DenseVector;

import java.util.List;
import java.util.logging.Logger;

/**
 * Demo 2 loader: inserts the fictional sample policies through the Jakarta Data
 * {@link PolicyRepository}. Insert-only and idempotent, exactly like the
 * template demo: existing UUIDs are skipped and never updated.
 */
@ApplicationScoped
public class PolicyLoader {

    private static final Logger LOGGER =
            Logger.getLogger(PolicyLoader.class.getName());

    private EmbeddingModel embeddingModel;

    private PolicyRepository repository;

    PolicyLoader() {
    }

    @Inject
    PolicyLoader(EmbeddingModel embeddingModel, PolicyRepository repository) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
    }

    public void load() {
        List<PolicyDefinition> definitions =
                PolicyDefinition.readAll(PolicyDefinition.POLICIES_RESOURCE);

        LOGGER.info("[data] Initializing sample policies");

        int inserted = 0;
        int skipped = 0;

        for (PolicyDefinition definition : definitions) {
            if (repository.existsById(definition.id())) {
                LOGGER.info(() -> "Skipping existing policy: " + definition.label());
                skipped++;
                continue;
            }

            var embedding = embeddingModel.embed(definition.embeddingText()).content();

            Policy policy = definition.toPolicy(DenseVector.of(embedding.vector()));
            repository.insert(policy.forStorage());

            LOGGER.info(() -> "Inserted policy: " + definition.label());
            inserted++;
        }

        LOGGER.info("Policy initialization completed: "
                + inserted + " inserted, "
                + skipped + " skipped");
    }
}

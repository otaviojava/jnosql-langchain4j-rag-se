package com.github.otaviojava.rag.template;

import com.github.otaviojava.rag.model.Policy;
import com.github.otaviojava.rag.shared.PolicyDefinition;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.DenseVector;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;

import java.util.List;
import java.util.logging.Logger;

/**
 * Demo 1 loader: inserts the fictional sample policies through Eclipse JNoSQL's
 * {@link VectorTemplate}. Ingestion is insert-only and idempotent: policies whose
 * stable UUID already exists are skipped and never updated. To apply changes to an
 * already-inserted policy, recreate the collection or add a new policy revision
 * with a new id.
 */
@ApplicationScoped
public class PolicyLoader {

    private static final Logger LOGGER =
            Logger.getLogger(PolicyLoader.class.getName());

    private EmbeddingModel embeddingModel;

    private VectorTemplate template;

    PolicyLoader() {
    }

    @Inject
    PolicyLoader(EmbeddingModel embeddingModel, VectorTemplate template) {
        this.embeddingModel = embeddingModel;
        this.template = template;
    }

    public void load() {
        List<PolicyDefinition> definitions =
                PolicyDefinition.readAll(PolicyDefinition.POLICIES_RESOURCE);

        LOGGER.info("[template] Initializing sample policies");

        int inserted = 0;
        int skipped = 0;

        for (PolicyDefinition definition : definitions) {
            if (template.find(Policy.class, definition.id()).isPresent()) {
                LOGGER.info(() -> "Skipping existing policy: " + definition.label());
                skipped++;
                continue;
            }

            var embedding = embeddingModel.embed(definition.embeddingText()).content();

            template.insert(definition.toPolicy(
                    DenseVector.of(embedding.vector())).forStorage());

            LOGGER.info(() -> "Inserted policy: " + definition.label());
            inserted++;
        }

        LOGGER.info("Policy initialization completed: "
                + inserted + " inserted, "
                + skipped + " skipped");
    }
}

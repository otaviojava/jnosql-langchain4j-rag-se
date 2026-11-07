
package com.github.otaviojava.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.DenseVector;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Loads the fictional sample policies from {@code policies.json} into Qdrant
 * through Eclipse JNoSQL. Ingestion is insert-only and idempotent: policies whose
 * stable UUID already exists are skipped and never updated. To apply changes to an
 * already-inserted policy, recreate the collection or add a new policy revision
 * with a new id.
 */
@ApplicationScoped
public class PolicyLoader {

    static final String POLICIES_RESOURCE = "policies.json";

    private static final Logger LOGGER =
            Logger.getLogger(PolicyLoader.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        List<PolicyDefinition> definitions = readDefinitions(POLICIES_RESOURCE);

        LOGGER.info("Initializing sample policies");

        int inserted = 0;
        int skipped = 0;

        for (PolicyDefinition definition : definitions) {
            if (template.find(Policy.class, definition.id()).isPresent()) {
                LOGGER.info(() -> "Skipping existing policy: "
                        + definition.title());
                skipped++;
                continue;
            }

            var embedding = embeddingModel.embed(
                    definition.title() + "\n" + definition.content()
            ).content();

            template.insert(new Policy(
                    definition.id(),
                    definition.title(),
                    definition.content(),
                    definition.department(),
                    definition.version(),
                    definition.active(),
                    DenseVector.of(embedding.vector())
            ));

            LOGGER.info(() -> "Inserted policy: " + definition.title());
            inserted++;
        }

        LOGGER.info("Policy initialization completed: "
                + inserted + " inserted, "
                + skipped + " skipped");
    }

    List<PolicyDefinition> readDefinitions(String resource) {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Policy resource not found on classpath: " + resource);
            }
            return MAPPER.readValue(input, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Malformed policy resource: " + resource, e);
        }
    }

    record PolicyDefinition(
            UUID id,
            String title,
            String content,
            String department,
            String version,
            boolean active
    ) {
        PolicyDefinition {
            Objects.requireNonNull(id, "Policy id is required");
            Objects.requireNonNull(title, "Policy title is required");
            Objects.requireNonNull(content, "Policy content is required");
        }
    }
}

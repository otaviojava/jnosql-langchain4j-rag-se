
package com.github.otaviojava.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.DenseVector;
import org.eclipse.jnosql.mapping.vector.Vector;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
            String label = definition.policyCode() + " v" + definition.version();

            if (template.find(Policy.class, definition.id()).isPresent()) {
                LOGGER.info(() -> "Skipping existing policy: " + label);
                skipped++;
                continue;
            }

            var embedding = embeddingModel.embed(
                    definition.title() + "\n" + definition.content()
            ).content();

            template.insert(definition.toPolicy(
                    DenseVector.of(embedding.vector())).forStorage());

            LOGGER.info(() -> "Inserted policy: " + label);
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
            List<PolicyDefinition> definitions = MAPPER.readValue(
                    input, new TypeReference<>() {
                    });
            definitions.forEach(definition -> definition.toPolicy(null));
            return definitions;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Malformed policy resource: " + resource, e);
        }
    }

    record PolicyDefinition(
            UUID id,
            String policyCode,
            String title,
            String content,
            String department,
            String version,
            String effectiveFrom,
            String effectiveUntil
    ) {
        PolicyDefinition {
            Objects.requireNonNull(id, "Policy id is required");
            Objects.requireNonNull(policyCode, "Policy code is required");
            Objects.requireNonNull(title, "Policy title is required");
            Objects.requireNonNull(content, "Policy content is required");
            Objects.requireNonNull(version, "Policy version is required");
            Objects.requireNonNull(effectiveFrom, "Policy effectiveFrom is required");
        }

        LocalDate from() {
            return parse(effectiveFrom);
        }

        LocalDate until() {
            return effectiveUntil == null ? null : parse(effectiveUntil);
        }

        Policy toPolicy(Vector embedding) {
            return new Policy(id, policyCode, title, content, department,
                    version, from(), until(), embedding);
        }

        private static LocalDate parse(String date) {
            try {
                return LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw new IllegalStateException("Invalid policy date: " + date, e);
            }
        }
    }
}

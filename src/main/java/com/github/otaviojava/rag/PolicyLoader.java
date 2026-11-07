
package com.github.otaviojava.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.DenseVector;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class PolicyLoader {

    private static final Logger LOGGER =
            Logger.getLogger(PolicyLoader.class.getName());

    @Inject
    private EmbeddingModel embeddingModel;

    @Inject
    private VectorTemplate template;

    public void load() {
        List<PolicyDraft> drafts = List.of(
                new PolicyDraft(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                        "Card Transaction Dispute",
                        """
                        Customers may report unauthorized credit card
                        transactions within 30 days of the transaction.
                        Support agents must verify the customer's identity,
                        register the dispute, and refer the case to the
                        fraud investigation team.
                        """,
                        "Payments"
                ),
                new PolicyDraft(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                        "Fraud Investigation",
                        """
                        When a customer reports a potentially fraudulent
                        transaction, support agents must record the
                        transaction details and notify the fraud team.
                        The fraud team evaluates the evidence and
                        determines whether additional verification
                        is required.
                        """,
                        "Fraud Prevention"
                ),
                new PolicyDraft(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                        "Customer Identity Verification",
                        """
                        Before disclosing sensitive account information
                        or initiating a transaction dispute, support
                        agents must verify the customer's identity
                        using the institution's approved procedure.
                        """,
                        "Compliance"
                )
        );

        LOGGER.info("Initializing sample policies");

        int inserted = 0;
        int skipped = 0;

        for (PolicyDraft draft : drafts) {
            if (template.find(Policy.class, draft.id()).isPresent()) {
                LOGGER.info(() -> "Skipping existing policy: "
                        + draft.title());
                skipped++;
                continue;
            }

            var embedding = embeddingModel.embed(
                    draft.title() + "\n" + draft.content()
            ).content();

            Policy policy = new Policy(
                    draft.id(),
                    draft.title(),
                    draft.content(),
                    draft.department(),
                    "1.0",
                    true,
                    DenseVector.of(embedding.vector())
            );

            template.insert(policy);

            LOGGER.info(() -> "Inserted policy: " + draft.title());
            inserted++;
        }

        LOGGER.info("Policy initialization completed: "
                + inserted + " inserted, "
                + skipped + " skipped");
    }

    private record PolicyDraft(
            UUID id,
            String title,
            String content,
            String department
    ) {
    }
}

package com.github.otaviojava.rag;

import org.eclipse.jnosql.mapping.vector.DenseVector;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
class QdrantIntegrationTest {

    private static final String[] CONFIGURATION_KEYS = {
            "jnosql.vector.database",
            "jnosql.qdrant.host",
            "jnosql.qdrant.port",
            "jnosql.qdrant.collection.distance",
            "jnosql.qdrant.collection.vector.size"
    };

    @Container
    private static final QdrantContainer QDRANT =
            new QdrantContainer("qdrant/qdrant:v1.14.1");

    private static final Map<String, String> ORIGINAL_PROPERTIES = new HashMap<>();

    @BeforeAll
    static void configureQdrant() {
        for (String key : CONFIGURATION_KEYS) {
            ORIGINAL_PROPERTIES.put(key, System.getProperty(key));
        }

        System.setProperty("jnosql.vector.database", "policies_integration_test");
        System.setProperty("jnosql.qdrant.host", QDRANT.getHost());
        System.setProperty("jnosql.qdrant.port", Integer.toString(QDRANT.getGrpcPort()));
        System.setProperty("jnosql.qdrant.collection.distance", "COSINE");
        System.setProperty("jnosql.qdrant.collection.vector.size", "3");
    }

    @AfterAll
    static void restoreConfiguration() {
        ORIGINAL_PROPERTIES.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    @Test
    void insertsAndRetrievesPolicyIncludingItsVector() {
        try (WeldContainer container = new Weld().initialize()) {
            VectorTemplate template = container.select(VectorTemplate.class).get();
            Policy policy = new Policy(
                    "policy-001",
                    "Data retention",
                    "Retain customer records for the required period.",
                    DenseVector.of(0.1f, 0.2f, 0.3f));

            template.insert(policy);

            Policy retrieved = template.find(Policy.class, policy.getId()).orElseThrow();

            assertThat(retrieved.getId()).isEqualTo(policy.getId());
            assertThat(retrieved.getTitle()).isEqualTo(policy.getTitle());
            assertThat(retrieved.getContent()).isEqualTo(policy.getContent());
            assertThat(retrieved.getVector().dimensions()).isEqualTo(3);
            float[] values = retrieved.getVector().values();
            assertThat(values[0]).isCloseTo(0.1f / (float) Math.sqrt(0.14), within(1.0e-6f));
            assertThat(values[1]).isCloseTo(0.2f / (float) Math.sqrt(0.14), within(1.0e-6f));
            assertThat(values[2]).isCloseTo(0.3f / (float) Math.sqrt(0.14), within(1.0e-6f));
        }
    }
}

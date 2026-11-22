package com.github.otaviojava.rag.shared;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyDefinitionTest {

    private static final UUID CARD_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @Test
    void shouldParseVersionedPoliciesFromClasspath() {
        List<PolicyDefinition> definitions =
                PolicyDefinition.readAll(PolicyDefinition.POLICIES_RESOURCE);

        assertThat(definitions).hasSize(4);
        assertThat(definitions).extracting(PolicyDefinition::id)
                .doesNotHaveDuplicates();
        assertThat(definitions).extracting(PolicyDefinition::label)
                .containsExactly("CARD-DISPUTE v1.0", "CARD-DISPUTE v2.0",
                        "FRAUD-INVESTIGATION v1.0", "IDENTITY-VERIFICATION v1.0");

        var v1 = definitions.get(0);
        assertThat(v1.id()).isEqualTo(CARD_ID);
        assertThat(v1.from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(v1.until()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v1.content()).contains("30 days");

        var v2 = definitions.get(1);
        assertThat(v2.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(v2.until()).isNull();
        assertThat(v2.content()).contains("60 days");
    }

    @Test
    void shouldEmbedOnlyTitleAndContent() {
        PolicyDefinition.readAll(PolicyDefinition.POLICIES_RESOURCE).forEach(d ->
                assertThat(d.embeddingText())
                        .isEqualTo(d.title() + "\n" + d.content())
                        .doesNotContain("550e8400")
                        .doesNotContain("2025-01-01"));
    }

    @Test
    void shouldFailClearlyWhenResourceIsMissing() {
        assertThatThrownBy(() -> PolicyDefinition.readAll("missing-policies.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-policies.json");
    }

    @Test
    void shouldFailClearlyWhenResourceIsMalformed() {
        assertThatThrownBy(() -> PolicyDefinition.readAll("META-INF/beans.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed policy resource");
    }
}

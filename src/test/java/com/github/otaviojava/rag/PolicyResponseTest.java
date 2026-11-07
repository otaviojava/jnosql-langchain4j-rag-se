package com.github.otaviojava.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyResponseTest {

    @Test
    void shouldExposeStructuredFields() {
        var response = new PolicyResponse("Answer", true, List.of("Escalate"));

        assertThat(response.answer()).isEqualTo("Answer");
        assertThat(response.requiresHumanReview()).isTrue();
        assertThat(response.recommendations()).containsExactly("Escalate");
    }
}

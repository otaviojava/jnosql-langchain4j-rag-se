package com.github.otaviojava.rag;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate UNTIL = LocalDate.of(2026, 7, 1);

    private static Policy policy(LocalDate from, LocalDate until) {
        return new Policy(UUID.randomUUID(), "CODE", "Title", "Content",
                "Dept", "1.0", from, until, null);
    }

    @Test
    void shouldApplyOnStartingDate() {
        assertThat(policy(FROM, UNTIL).appliesOn(FROM)).isTrue();
    }

    @Test
    void shouldNotApplyBeforeStartingDate() {
        assertThat(policy(FROM, UNTIL).appliesOn(FROM.minusDays(1))).isFalse();
    }

    @Test
    void shouldNotApplyOnExclusiveEndingDate() {
        var policy = policy(FROM, UNTIL);
        assertThat(policy.appliesOn(UNTIL.minusDays(1))).isTrue();
        assertThat(policy.appliesOn(UNTIL)).isFalse();
    }

    @Test
    void shouldRemainApplicableWithoutEndingDate() {
        assertThat(policy(FROM, null).appliesOn(LocalDate.of(2999, 1, 1))).isTrue();
    }

    @Test
    void shouldRejectInvalidPeriods() {
        assertThatThrownBy(() -> policy(FROM, FROM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(FROM, FROM.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullDate() {
        assertThatThrownBy(() -> policy(FROM, null).appliesOn(null))
                .isInstanceOf(NullPointerException.class);
    }
}

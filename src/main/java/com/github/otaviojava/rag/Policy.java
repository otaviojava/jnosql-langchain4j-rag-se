package com.github.otaviojava.rag;

import jakarta.nosql.Column;
import jakarta.nosql.Convert;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import org.eclipse.jnosql.mapping.vector.Vector;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable policy revision. {@code id} identifies the revision, while
 * {@code policyCode} is the stable business identity shared by all revisions.
 * A revision applies from {@code effectiveFrom} (inclusive) until
 * {@code effectiveUntil} (exclusive); a null end date means open-ended.
 */
@Entity("policies")
public record Policy(
        @Id UUID id,
        @Column String policyCode,
        @Column String title,
        @Column String content,
        @Column String department,
        @Column String version,
        @Column LocalDate effectiveFrom,
        @Column @Convert(OpenEndedDateConverter.class) LocalDate effectiveUntil,
        @Column Vector embedding
) {

    public Policy {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(policyCode, "policyCode is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil must be after effectiveFrom: "
                            + effectiveFrom + " - " + effectiveUntil);
        }
    }

    public boolean appliesOn(LocalDate date) {
        Objects.requireNonNull(date);

        return !date.isBefore(effectiveFrom)
                && (effectiveUntil == null
                || date.isBefore(effectiveUntil));
    }

    /**
     * Returns the instance to hand to JNoSQL: an open-ended period is given the
     * {@link OpenEndedDateConverter#OPEN_ENDED} sentinel because null values
     * cannot be persisted in the collection shared with LangChain4j.
     */
    public Policy forStorage() {
        return effectiveUntil != null ? this
                : new Policy(id, policyCode, title, content, department,
                version, effectiveFrom, OpenEndedDateConverter.OPEN_ENDED, embedding);
    }
}

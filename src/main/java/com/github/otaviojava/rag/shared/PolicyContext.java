package com.github.otaviojava.rag.shared;

import com.github.otaviojava.rag.model.Policy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.time.LocalDate;

/** Business-date input and LLM context formatting shared by both retrievers. */
public final class PolicyContext {

    /** Invocation parameter carrying the date on which policies must apply. */
    public static final String BUSINESS_DATE = "businessDate";

    private PolicyContext() {
    }

    public static LocalDate businessDate(Query query) {
        var parameters = query.metadata() == null
                ? null : query.metadata().invocationParameters();
        Object date = parameters == null
                ? null : parameters.get(BUSINESS_DATE);
        if (date instanceof LocalDate localDate) {
            return localDate;
        }
        throw new IllegalArgumentException(
                "A LocalDate invocation parameter named '" + BUSINESS_DATE
                        + "' is required");
    }

    public static Content toContent(Policy policy) {
        String effective = policy.effectiveUntil() == null
                ? policy.effectiveFrom() + " onwards"
                : policy.effectiveFrom() + " until "
                + policy.effectiveUntil() + " (exclusive)";

        String text = """
                Policy code: %s
                Title: %s
                Version: %s
                Effective period: %s
                Department: %s

                %s
                """.formatted(
                policy.policyCode(),
                policy.title(),
                policy.version(),
                effective,
                policy.department(),
                policy.content()
        );

        return Content.from(TextSegment.from(text));
    }
}

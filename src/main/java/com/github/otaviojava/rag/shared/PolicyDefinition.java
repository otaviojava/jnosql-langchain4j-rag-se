package com.github.otaviojava.rag.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.otaviojava.rag.model.Policy;
import org.eclipse.jnosql.mapping.vector.Vector;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A policy as declared in {@code policies.json}: no embedding, dates as ISO strings.
 * Both demos read the same file; they differ only in how they persist it.
 */
public record PolicyDefinition(
        UUID id,
        String policyCode,
        String title,
        String content,
        String department,
        String version,
        String effectiveFrom,
        String effectiveUntil
) {

    public static final String POLICIES_RESOURCE = "policies.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PolicyDefinition {
        Objects.requireNonNull(id, "Policy id is required");
        Objects.requireNonNull(policyCode, "Policy code is required");
        Objects.requireNonNull(title, "Policy title is required");
        Objects.requireNonNull(content, "Policy content is required");
        Objects.requireNonNull(version, "Policy version is required");
        Objects.requireNonNull(effectiveFrom, "Policy effectiveFrom is required");
    }

    /** Reads and validates all policy definitions from the classpath. */
    public static List<PolicyDefinition> readAll(String resource) {
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

    /** Text embedded for semantic search: title and content only. */
    public String embeddingText() {
        return title + "\n" + content;
    }

    public String label() {
        return policyCode + " v" + version;
    }

    public LocalDate from() {
        return parse(effectiveFrom);
    }

    public LocalDate until() {
        return effectiveUntil == null ? null : parse(effectiveUntil);
    }

    public Policy toPolicy(Vector embedding) {
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

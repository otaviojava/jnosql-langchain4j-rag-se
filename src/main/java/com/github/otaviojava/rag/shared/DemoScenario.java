package com.github.otaviojava.rag.shared;

import java.time.LocalDate;
import java.util.List;

/** A demonstration question together with the business date it applies to. */
public record DemoScenario(String title, String question, LocalDate businessDate) {

    public static final DemoScenario HISTORICAL = new DemoScenario(
            "Scenario 1: Historical Policy",
            """
            A customer reported an unauthorized credit card transaction \
            20 days after a purchase made in May 2026. \
            What should our support team do?""",
            LocalDate.of(2026, 5, 15));

    public static final DemoScenario CURRENT = new DemoScenario(
            "Scenario 2: Current Policy",
            """
            A customer reported an unauthorized credit card transaction \
            45 days after a purchase made in August 2026. \
            Can the support team accept the report?""",
            LocalDate.of(2026, 8, 15));

    public static final DemoScenario UNSUPPORTED = new DemoScenario(
            "Scenario 3: Insufficient Knowledge",
            """
            A customer wants to reverse an international wire transfer \
            completed 45 days ago. What is the refund deadline and procedure?""",
            LocalDate.of(2026, 8, 15));

    public static final List<DemoScenario> ALL =
            List.of(HISTORICAL, CURRENT, UNSUPPORTED);

    public void print(PolicyResponse response) {
        System.out.println("\n=== " + title + " ===\n");
        System.out.println("Question: " + question);
        System.out.println("Applicable business date: " + businessDate);
        System.out.println("Answer: " + response.answer());
        System.out.println("Human review required: "
                + response.requiresHumanReview());
        System.out.println("Recommendations:");
        response.recommendations()
                .forEach(recommendation -> System.out.println("- " + recommendation));
    }
}

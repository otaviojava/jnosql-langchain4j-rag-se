package com.github.otaviojava.rag;

import dev.langchain4j.invocation.InvocationParameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.time.LocalDate;
import java.util.logging.Logger;

@ApplicationScoped
public class App {

    private static final Logger LOGGER =
            Logger.getLogger(App.class.getName());

    static final String HISTORICAL_QUESTION = """
            A customer reported an unauthorized credit card transaction \
            20 days after a purchase made in May 2026. \
            What should our support team do?""";
    static final LocalDate HISTORICAL_DATE = LocalDate.of(2026, 5, 15);

    static final String CURRENT_QUESTION = """
            A customer reported an unauthorized credit card transaction \
            45 days after a purchase made in August 2026. \
            Can the support team accept the report?""";
    static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 15);

    static final String UNSUPPORTED_QUESTION = """
            A customer wants to reverse an international wire transfer \
            completed 45 days ago. What is the refund deadline and procedure?""";
    static final LocalDate UNSUPPORTED_DATE = LocalDate.of(2026, 8, 15);

    @Inject
    private PolicyAssistant assistant;

    @Inject
    private PolicyLoader loader;

    public static void main(String[] args) {
        try (WeldContainer container = new Weld().initialize()) {
            container.select(App.class).get().start();
        }
    }

    public void start() {
        loader.load();

        run("Scenario 1: Historical Policy",
                HISTORICAL_QUESTION, HISTORICAL_DATE);
        run("Scenario 2: Current Policy",
                CURRENT_QUESTION, CURRENT_DATE);
        run("Scenario 3: Insufficient Knowledge",
                UNSUPPORTED_QUESTION, UNSUPPORTED_DATE);
    }

    private void run(String title, String question, LocalDate businessDate) {
        LOGGER.info(() -> "Running " + title);
        var parameters = InvocationParameters.from(
                PolicyRetriever.BUSINESS_DATE, businessDate);
        print(title, question, businessDate,
                assistant.analyze(question, parameters));
    }

    static void print(String title, String question, LocalDate businessDate,
                      PolicyResponse response) {
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

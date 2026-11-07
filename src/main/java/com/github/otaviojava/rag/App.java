package com.github.otaviojava.rag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.util.logging.Logger;

@ApplicationScoped
public class App {

    private static final Logger LOGGER =
            Logger.getLogger(App.class.getName());

    static final String SUPPORTED_QUESTION = """
            A customer reported an unauthorized credit card transaction \
            20 days after the purchase. What should our support team do?""";

    static final String UNSUPPORTED_QUESTION = """
            A customer wants to reverse an international wire transfer \
            completed 45 days ago. What is the refund deadline and procedure?""";

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

        run("Scenario 1: Supported Policy Question", SUPPORTED_QUESTION);
        run("Scenario 2: Unsupported Policy Question", UNSUPPORTED_QUESTION);
    }

    private void run(String title, String question) {
        LOGGER.info(() -> "Running " + title);
        print(title, question, assistant.analyze(question));
    }

    static void print(String title, String question, PolicyResponse response) {
        System.out.println("\n=== " + title + " ===\n");
        System.out.println("Question: " + question);
        System.out.println("Answer: " + response.answer());
        System.out.println("Human review required: "
                + response.requiresHumanReview());
        System.out.println("Recommendations:");
        response.recommendations()
                .forEach(recommendation -> System.out.println("- " + recommendation));
    }
}

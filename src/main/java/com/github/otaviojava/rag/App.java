package com.github.otaviojava.rag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

@ApplicationScoped
public class App {

    @Inject
    private PolicyAssistant assistant;

    @Inject
    private PolicyLoader loader;

    public static void main(String[] args) {
        try (WeldContainer container = new Weld().initialize()) {
            App app = container.select(App.class).get();
            app.start();
        }
    }

    public void start() {
        loader.load();
        PolicyResponse response = assistant.analyze("""
        A customer reported an unauthorized credit card
        transaction 20 days after the purchase.

        What should our support team do?
        """);

        System.out.println(response);
        System.out.println("Recommendations:");
        response.recommendations()
                .forEach(System.out::println);
    }
}

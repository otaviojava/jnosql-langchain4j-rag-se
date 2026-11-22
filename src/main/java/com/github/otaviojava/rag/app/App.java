package com.github.otaviojava.rag.app;

import com.github.otaviojava.rag.shared.DemoScenario;
import com.github.otaviojava.rag.shared.PolicyContext;
import com.github.otaviojava.rag.template.PolicyAssistant;
import com.github.otaviojava.rag.template.PolicyLoader;
import dev.langchain4j.invocation.InvocationParameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.util.logging.Logger;

/** Demo 1: RAG with Eclipse JNoSQL's {@code VectorTemplate}. */
@ApplicationScoped
public class App {

    private static final Logger LOGGER =
            Logger.getLogger(App.class.getName());

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

        for (DemoScenario scenario : DemoScenario.ALL) {
            LOGGER.info(() -> "Running " + scenario.title());
            var parameters = InvocationParameters.from(
                    PolicyContext.BUSINESS_DATE, scenario.businessDate());
            scenario.print(assistant.analyze(scenario.question(), parameters));
        }
    }
}

package com.github.otaviojava.rag.app;

import com.github.otaviojava.rag.data.PolicyAssistant;
import com.github.otaviojava.rag.data.PolicyLoader;
import com.github.otaviojava.rag.shared.DemoScenario;
import com.github.otaviojava.rag.shared.PolicyContext;
import dev.langchain4j.invocation.InvocationParameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.util.logging.Logger;

/** Demo 2: the same RAG scenarios with Jakarta Data repositories. */
@ApplicationScoped
public class DataApp {

    private static final Logger LOGGER =
            Logger.getLogger(DataApp.class.getName());

    @Inject
    private PolicyAssistant assistant;

    @Inject
    private PolicyLoader loader;

    public static void main(String[] args) {
        try (WeldContainer container = new Weld().initialize()) {
            container.select(DataApp.class).get().start();
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

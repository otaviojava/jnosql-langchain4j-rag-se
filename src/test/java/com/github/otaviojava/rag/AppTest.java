package com.github.otaviojava.rag;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void startsCdiInjectsVectorTemplateAndLoadsConfiguration() {
        WeldContainer container = new Weld().initialize();

        try (container) {
            App app = container.select(App.class).get();

            assertThat(app.vectorTemplate()).isNotNull();
            assertThat(ConfigProvider.getConfig().getValue("jnosql.vector.database", String.class))
                    .isEqualTo("policies_test");
        }

        assertThat(container.isRunning()).isFalse();
    }
}

package com.github.otaviojava.rag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jnosql.mapping.vector.VectorTemplate;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

@ApplicationScoped
public class App {

    @Inject
    private VectorTemplate vectorTemplate;

    public static void main(String[] args) {
        try (WeldContainer container = new Weld().initialize()) {
            container.select(App.class).get().start();
        }
    }

    public void start() {
        if (vectorTemplate == null) {
            throw new IllegalStateException("VectorTemplate was not injected");
        }
        System.out.println("Weld started; VectorTemplate injected.");
    }

    VectorTemplate vectorTemplate() {
        return vectorTemplate;
    }
}

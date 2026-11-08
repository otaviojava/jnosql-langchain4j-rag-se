
package com.github.otaviojava.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class LangChain4jConfiguration {

    @Produces
    @ApplicationScoped
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Produces
    @ApplicationScoped
    public EmbeddingStore<TextSegment> embeddingStore(
            @ConfigProperty(name = "jnosql.qdrant.host")
            String host,
            @ConfigProperty(name = "jnosql.qdrant.port")
            int port,
            @ConfigProperty(name = "jnosql.vector.database")
            String collection,
            @ConfigProperty(name = "rag.qdrant.tls",
                    defaultValue = "false")
            boolean tls) {

        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collection)
                .useTls(tls)
                .build();
    }

}

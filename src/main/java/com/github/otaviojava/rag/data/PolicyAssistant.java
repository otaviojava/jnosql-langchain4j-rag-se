package com.github.otaviojava.rag.data;

import com.github.otaviojava.rag.shared.PolicyPrompts;
import com.github.otaviojava.rag.shared.PolicyResponse;
import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;

/** Demo 2 AI service: retrieval is backed by {@link PolicyRetriever} (Jakarta Data). */
@RegisterAIService(scope = ApplicationScoped.class, contentRetrieverName = "dataPolicyRetriever")
public interface PolicyAssistant {

    @SystemMessage(PolicyPrompts.SYSTEM_MESSAGE)
    @UserMessage(PolicyPrompts.USER_MESSAGE)
    PolicyResponse analyze(@V("question") String question,
                           InvocationParameters parameters);
}

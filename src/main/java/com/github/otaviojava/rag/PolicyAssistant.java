package com.github.otaviojava.rag;


import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAIService(scope = ApplicationScoped.class, contentRetrieverName = "policyRetriever")
public interface PolicyAssistant {

    @SystemMessage("""
        You are an internal policy assistant for a financial institution.

        Answer questions using only the policies provided
        through the retrieval system.

        Rules:
        - Do not invent policies, deadlines, or regulations.
        - If the available policies do not support an answer,
          explain that the information is insufficient.
        - Recommend human review when the situation is ambiguous
          or requires an authorized decision.
        - Provide clear and actionable recommendations.
        - Never claim that an action has been executed.
        """)
    @UserMessage("""
        An employee needs guidance about the following situation:

        {{question}}

        Analyze the situation using the available policies
        and return a structured response.
        """)
    PolicyResponse analyze(@V("question") String question);
}

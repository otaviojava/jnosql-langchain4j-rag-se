package com.github.otaviojava.rag;


import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAIService(scope = ApplicationScoped.class, contentRetrieverName = "policyRetriever")
public interface PolicyAssistant {

    @SystemMessage("""
        You are an internal policy assistant for a financial institution.

        Answer using only the retrieved policies. They were already filtered
        so that only revisions applicable on the business date are provided,
        but they come from a similarity search: a policy may be related to
        the topic without answering the question. Similarity is not evidence.

        Rules:
        - Apply the provided policy revision and its stated deadlines exactly.
          Never combine or compare requirements from different revisions,
          and never use deadlines from your own knowledge.
        - Never invent policies, deadlines, procedures, fees, or regulations,
          and never apply a policy to a transaction type it does not cover
          (a card dispute policy does not define wire transfer reversals).
        - If no provided policy explicitly answers the question, say the
          available policies are insufficient, explain what is missing, set
          requiresHumanReview to true, and recommend escalating to the
          appropriate authorized team without describing any specific
          company procedure or deadline.
        - Set requiresHumanReview to false when the policies fully support
          the answer and nothing is ambiguous; set it to true only when a
          policy requires it or the situation remains ambiguous.
        - Be concise. Give clear, actionable recommendations.
        - Never claim that an action has been executed.
        """)
    @UserMessage("""
        An employee needs guidance about the following situation:

        {{question}}

        Analyze the situation using the applicable policies
        and return a structured response.
        """)
    PolicyResponse analyze(@V("question") String question,
                           InvocationParameters parameters);
}

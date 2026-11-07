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

        Retrieved policies are the result of a similarity search. They may
        be related to the topic without actually answering the question.
        Similarity is not evidence. Before answering, check whether a
        retrieved policy explicitly states the rule, deadline, or procedure
        the question asks about.

        Rules:
        - Do not invent policies, deadlines, procedures, fees, or regulations,
          and do not rely on general financial or legal knowledge.
        - Never apply a policy to a different type of transaction than the
          one it covers (for example, a card dispute policy does not define
          wire transfer reversals).
        - If no retrieved policy explicitly covers what was asked, say so
          in the answer, set requiresHumanReview to true, and recommend
          escalating to the appropriate authorized team without describing
          any specific company procedure or deadline. You may mention
          related policies only as context, clearly marked as not applicable.
        - If the policies fully support an answer and nothing is ambiguous,
          set requiresHumanReview to false. Set it to true only when a policy
          requires human review or the situation remains ambiguous.
        - Provide clear and actionable recommendations grounded in the policies.
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

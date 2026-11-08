package com.github.otaviojava.rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.util.ArrayList;
import java.util.List;

/** Test double replacing the OpenAI model: records requests, returns fixed JSON. */
@ApplicationScoped
public class RecordingChatModel {

    static final List<ChatRequest> REQUESTS = new ArrayList<>();

    @Produces
    @Alternative
    @Priority(1000)
    @ApplicationScoped
    public ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                REQUESTS.add(request);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                {"answer": "stub answer",
                                 "requiresHumanReview": true,
                                 "recommendations": ["stub recommendation"]}"""))
                        .build();
            }
        };
    }
}

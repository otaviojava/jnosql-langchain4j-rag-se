package com.github.otaviojava.rag.shared;

import java.util.List;

public record PolicyResponse(
        String answer,
        boolean requiresHumanReview,
        List<String> recommendations
) {
}
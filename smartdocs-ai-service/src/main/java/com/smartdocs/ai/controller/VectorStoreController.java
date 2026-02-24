package com.smartdocs.ai.controller;

import com.smartdocs.ai.service.PineconeVectorStoreService;
import com.smartdocs.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.smartdocs.ai.service.DocumentQnAService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/ai")
public class VectorStoreController {

    @Autowired
    private PineconeVectorStoreService pineconeService;

    @Autowired
    private DocumentQnAService questionAnsweringService;

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.toString();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Core Endpoints
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Precise Q&A (structured response format) — the primary user-facing endpoint.
     * Internally routes to SQL (FACTUAL), Pinecone (SEMANTIC), or Cross-doc
     * (CROSS_DOCUMENT).
     */
    @GetMapping("/answers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> answerQuestion(@RequestParam("query") String query) {
        String userEmail = getCurrentUserEmail();
        log.info("Q&A Request: {} [{}]", query, userEmail);

        DocumentQnAService.AnswerResult result = questionAnsweringService.answerQuestion(query, userEmail);

        if (result.isSuccessful()) {
            Map<String, Object> data = Map.of(
                    "query", query,
                    "answer", result.getAnswer(),
                    "route_type", result.getRouteType(),
                    "sources_count", result.getSourceDocuments().size(),
                    "type", "precise_answer");
            return ResponseEntity.ok(ApiResponse.success(data, "Answer generated successfully"));
        } else {
            Map<String, Object> data = Map.of(
                    "query", query,
                    "message", result.getMessage(),
                    "status", result.getStatus(),
                    "type", "no_answer");
            return ResponseEntity.ok(ApiResponse.success(data, result.getMessage()));
        }
    }

    /**
     * Semantic document search (Pinecone only — no LLM generation)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PineconeVectorStoreService.SimilarDocument>>> search(
            @RequestParam("query") String query,
            @RequestParam(name = "maxResults", defaultValue = "5") int maxResults) {
        String userEmail = getCurrentUserEmail();
        List<PineconeVectorStoreService.SimilarDocument> results = pineconeService.searchSimilar(query, userEmail,
                maxResults);
        return ResponseEntity.ok(ApiResponse.success(results, "Search completed successfully"));
    }
}

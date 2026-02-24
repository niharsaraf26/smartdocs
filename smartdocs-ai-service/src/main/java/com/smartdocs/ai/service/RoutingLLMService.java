package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight LLM service dedicated to query classification.
 * Uses Groq's llama-3.1-8b-instant — fast (~100ms), free tier, perfect for
 * simple classification.
 *
 * Returns a RoutingResult containing:
 * - QueryType (FACTUAL, SEMANTIC, CROSS_DOCUMENT)
 * - fieldHint (extracted field name for FACTUAL queries, null otherwise)
 */
@Slf4j
@Service
public class RoutingLLMService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${groq.routing-model:llama-3.1-8b-instant}")
    private String routingModel;

    public RoutingLLMService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private static final String ROUTING_PROMPT = """
            You are a query classifier for a document management system. Classify the user's question into exactly ONE category and extract the field name(s) and document types if applicable.

            Categories:
            - FACTUAL: Direct data lookup for specific piece(s) of information stored as structured metadata (e.g., a name, ID number, date, phone number, address, roll number). These are questions where the answer is one or more values that can be looked up directly from a single document without calculation.
              Examples: "What is my PAN number?", "What is my mother's name?", "What are my parent's names?"

            - SEMANTIC: Questions that require reading and understanding the full document content of a specific document. This includes summaries, explanations, sentiments or details that cannot be answered by metadata fields.
              Examples: "Summarize my marksheet", "What subjects did I study?", "Explain the terms in my invoice"

            - CROSS_DOCUMENT: Questions that compare, correlate, or AGGREGATE information (such as sums, totals, averages, counts, or total spending) across multiple documents. If the user asks for a total or aggregate, it MUST be CROSS_DOCUMENT.
              Examples: "Is my name the same on my Aadhaar and PAN?", "Compare my two invoices", "Show all my documents", "How much did I spend on food in total?", "What is my total expenditure across all bills?"

            For FACTUAL queries, extract the EXACT field names from this canonical list:
            Identity: person_name, father_name, mother_name, guardian_name, date_of_birth, gender, id_number, address, phone, email, issue_date, expiry_date
            Education: person_name, roll_number, institution_name, exam_name, year, result, total_marks, marks_obtained, percentage, grade, subjects
            Financial: provider_name, customer_name, invoice_number, bill_date, total_amount, tax_amount, net_amount, payment_status
            Bank: bank_name, account_holder, account_number, opening_balance, closing_balance
            Salary: employee_name, employer_name, designation, basic_salary, net_salary, gross_salary
            Legal: party_name_1, party_name_2, document_title, effective_date, expiry_date, policy_number, premium_amount
            Government: person_name, assessment_year, total_income, tax_paid, refund_amount, form_type, registration_number
            Medical: patient_name, doctor_name, hospital_name, diagnosis, prescription, lab_test_name, lab_result

            Examples of field mapping:
            "What is my name?" → fields: ["person_name"]
            "What are my parent's names?" → fields: ["father_name", "mother_name"]
            "What is my PAN number?" → fields: ["id_number"]
            "What is my Aadhaar number?" → fields: ["id_number"]
            "When was I born?" → fields: ["date_of_birth"]
            "What is my roll number?" → fields: ["roll_number"]
            "What is my salary?" → fields: ["net_salary"]
            "Where do I live?" → fields: ["address"]
            "What is my phone number?" → fields: ["phone"]
            "What is my percentage?" → fields: ["percentage"]
            "What marks did I get?" → fields: ["marks_obtained", "total_marks"]

            For CROSS_DOCUMENT queries, also extract which document types from the user's query are likely relevant. Use EXACTLY these enum names: IDENTITY_DOCUMENT, EDUCATION_DOCUMENT, FINANCIAL_BILL, BANK_STATEMENT, SALARY_SLIP, LEGAL_DOCUMENT, GOVERNMENT_DOCUMENT, MEDICAL_DOCUMENT, OTHER. Return null if all document types are relevant.
            A question like "How much did I spend on food?" → document_types: ["FINANCIAL_BILL"]
            A question like "Is my name same on Aadhaar and PAN?" → document_types: ["IDENTITY_DOCUMENT"]
            A question like "Compare my marksheet and salary slip" → document_types: ["EDUCATION_DOCUMENT", "SALARY_SLIP"]
            A question like "Show me all my documents" → document_types: null

            Respond with ONLY a JSON object, no other text:
            {"type": "FACTUAL|SEMANTIC|CROSS_DOCUMENT", "fields": ["field1"] or null, "document_types": ["ENUM_NAME"] or null}

            User question: %s
            """;

    /**
     * Classify a user query using the lightweight routing LLM.
     * On failure, defaults to SEMANTIC (safest fallback — will always attempt an
     * answer).
     */
    public RoutingResult classifyQuery(String query) {
        try {
            log.info("Routing LLM: classifying query with {} ...", routingModel);

            String prompt = String.format(ROUTING_PROMPT, query);

            Map<String, Object> requestBody = Map.of(
                    "model", routingModel,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)),
                    "temperature", 0.0,
                    "max_tokens", 150,
                    "stream", false);

            String response = webClient
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseRoutingResponse(response);

        } catch (Exception e) {
            log.error("Routing LLM failed, defaulting to SEMANTIC: {}", e.getMessage());
            return new RoutingResult(QueryRouter.QueryType.SEMANTIC, List.of(), null);
        }
    }

    @SuppressWarnings("unchecked")
    private RoutingResult parseRoutingResponse(String response) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            if (responseMap.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) responseMap.get("error");
                log.error("Routing LLM API error: {}", error.get("message"));
                return new RoutingResult(QueryRouter.QueryType.SEMANTIC, List.of(), null);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("Routing LLM: no choices in response");
                return new RoutingResult(QueryRouter.QueryType.SEMANTIC, List.of(), null);
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = ((String) message.get("content")).trim();

            // Strip markdown code fences if the LLM wraps JSON in ```json ... ```
            if (content.startsWith("```")) {
                content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }

            log.info("Routing LLM raw response: {}", content);

            // Parse the JSON classification
            Map<String, Object> classification = objectMapper.readValue(content, Map.class);

            String typeStr = ((String) classification.getOrDefault("type", "SEMANTIC")).toUpperCase();

            // Parse fields — handle both array and single string for robustness
            List<String> fields = new ArrayList<>();
            Object fieldsObj = classification.get("fields");
            if (fieldsObj instanceof List<?> fieldList) {
                for (Object f : fieldList) {
                    String s = String.valueOf(f).trim();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                        fields.add(s);
                    }
                }
            } else if (fieldsObj instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
                fields.add(s);
            }
            // Also handle legacy "field" key in case LLM returns it
            Object fieldObj = classification.get("field");
            if (fields.isEmpty() && fieldObj instanceof String s && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
                fields.add(s);
            }

            // Parse document_types — only for CROSS_DOCUMENT queries
            List<String> documentTypes = null;
            Object docTypesObj = classification.get("document_types");
            if (docTypesObj instanceof List<?> dtList) {
                List<String> parsed = new ArrayList<>();
                for (Object dt : dtList) {
                    String s = String.valueOf(dt).trim().toUpperCase();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                        parsed.add(s);
                    }
                }
                if (!parsed.isEmpty()) {
                    documentTypes = parsed;
                }
            }

            QueryRouter.QueryType queryType;
            try {
                queryType = QueryRouter.QueryType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Routing LLM returned unknown type '{}', defaulting to SEMANTIC", typeStr);
                queryType = QueryRouter.QueryType.SEMANTIC;
            }

            log.info("Routing result: type={}, fields={}, document_types={}", queryType, fields, documentTypes);
            return new RoutingResult(queryType, fields, documentTypes);

        } catch (Exception e) {
            log.error("Failed to parse routing response, defaulting to SEMANTIC: {}", e.getMessage());
            return new RoutingResult(QueryRouter.QueryType.SEMANTIC, List.of(), null);
        }
    }

    /**
     * Result of query routing.
     * - type: the route (FACTUAL, SEMANTIC, CROSS_DOCUMENT)
     * - fieldHints: for FACTUAL — the metadata field names to search (e.g.
     * ["pan_number"])
     * - documentTypes: for CROSS_DOCUMENT — the document types to filter by (e.g.
     * ["invoice", "receipt"]).
     * null means all document types are relevant (no filter).
     */
    public record RoutingResult(QueryRouter.QueryType type, List<String> fieldHints, List<String> documentTypes) {
    }
}

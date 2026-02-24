package com.smartdocs.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    @Value("${gemini.api.temperature}")
    private double temperature;

    public GeminiService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process document with Gemini AI — extracts 3 outputs:
     * 1. structured_fields — key-value pairs for SQL metadata
     * 2. prose_summary — clean natural language for vector embedding
     * 3. full_text — verbatim transcription for LLM context
     */
    public GeminiResponse processDocument(byte[] fileBytes, String contentType, String filename) {
        try {
            log.info("Processing document with Gemini model: {}", model);
            log.info("File: {} ({} bytes)", filename, fileBytes.length);

            String base64File = Base64.getEncoder().encodeToString(fileBytes);

            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[] {
                            Map.of("parts", new Object[] {
                                    Map.of("text", buildPrompt()),
                                    Map.of("inline_data", Map.of(
                                            "mime_type", contentType,
                                            "data", base64File))
                            })
                    },
                    "generationConfig", Map.of(
                            "response_mime_type", "application/json",
                            "temperature", temperature));

            String response = webClient
                    .post()
                    .uri(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseGeminiResponse(response, filename);

        } catch (Exception e) {
            log.error("Gemini processing failed for {}: {}", filename, e.getMessage(), e);
            return GeminiResponse.failed("Gemini API error: " + e.getMessage());
        }
    }

    /**
     * Hybrid RAG prompt — extracts 3 complementary outputs from any document type.
     */
    private String buildPrompt() {
        return """
                You are an expert document analyzer. Analyze the provided document and return a JSON object with exactly these sections:

                ## Output Format

                ```json
                {
                  "document_type": "One of: IDENTITY_DOCUMENT, EDUCATION_DOCUMENT, FINANCIAL_BILL, BANK_STATEMENT, SALARY_SLIP, LEGAL_DOCUMENT, GOVERNMENT_DOCUMENT, MEDICAL_DOCUMENT, OTHER",
                  "confidence": 0.95,

                  "structured_fields": [
                    {
                      "field_name": "exact_field_name_from_list_below",
                      "value": "extracted value exactly as it appears",
                      "type": "PERSON | DATE | ID_NUMBER | AMOUNT | ORGANIZATION | LOCATION | STATUS | TEXT"
                    }
                  ],

                  "prose_summary": "A clean, dense, natural-language paragraph summarizing ALL information in the document. Include every name, number, date, amount, and detail. This will be used for semantic search, so make it comprehensive and information-rich. Do NOT use JSON or bullet points here — write flowing prose.",

                  "full_text": "Complete verbatim transcription of every word visible in the document, exactly as printed. Include headers, labels, values, footnotes, and fine print. Do not paraphrase or skip anything."
                }
                ```

                ## STRICT Field Name Registry

                You MUST use ONLY the field names listed below. Do NOT invent new names. Pick the closest match from this list. If no field fits, skip it — the prose_summary and full_text will capture everything anyway.

                ### IDENTITY_DOCUMENT (Aadhaar, PAN, Passport, Driving License, Voter ID)
                person_name, father_name, mother_name, guardian_name, spouse_name, date_of_birth, gender, id_number, address, phone, email, issue_date, expiry_date, issuing_authority, nationality, blood_group, photo_available

                ### EDUCATION_DOCUMENT (Marksheet, Degree, Certificate, Transcript)
                person_name, father_name, mother_name, roll_number, institution_name, board_name, exam_name, year, result, subjects, total_marks, marks_obtained, percentage, grade, division, degree_name

                ### FINANCIAL_BILL (Invoice, Receipt, Bill, Utility Bill)
                provider_name, customer_name, invoice_number, bill_number, bill_date, due_date, billing_period, total_amount, tax_amount, discount, net_amount, payment_method, payment_status, items_description

                ### BANK_STATEMENT (Bank Statement, Passbook)
                bank_name, account_holder, account_number, ifsc_code, branch_name, statement_period, opening_balance, closing_balance, total_credits, total_debits, transaction_count

                ### SALARY_SLIP (Salary Slip, Pay Stub)
                employee_name, employer_name, employee_id, designation, department, pay_period, basic_salary, hra, da, other_allowances, gross_salary, pf_deduction, tax_deduction, other_deductions, net_salary

                ### LEGAL_DOCUMENT (Contract, Agreement, Insurance Policy, Rental Agreement, Will)
                party_name_1, party_name_2, document_title, effective_date, expiry_date, terms_summary, witness_name, notary_name, policy_number, premium_amount, coverage_amount, property_address

                ### GOVERNMENT_DOCUMENT (Tax Return, ITR, Form 16, Property Tax, Registration Certificate)
                person_name, assessment_year, financial_year, total_income, tax_paid, refund_amount, pan_number, form_type, filing_date, authority_name, registration_number

                ### MEDICAL_DOCUMENT (Prescription, Lab Report, Discharge Summary, Health Record)
                patient_name, doctor_name, hospital_name, diagnosis, prescription, date_of_visit, lab_test_name, lab_result, blood_group, allergies, discharge_date

                ## Rules
                1. Extract ONLY information explicitly present in the document
                2. Use ONLY field names from the registry above — do NOT create new field names
                3. The prose_summary must mention EVERY entity and detail — nothing should be lost
                4. The full_text must be a word-for-word transcription
                5. For amounts, include currency symbols
                6. For dates, preserve the original format
                7. CRITICAL: You MUST escape all double quotes and newlines inside string values to produce valid JSON. Do not break the JSON format.

                Now analyze the provided document.
                """;
    }

    /**
     * Parse Gemini API response — expects the 3-output JSON structure.
     */
    @SuppressWarnings("unchecked")
    private GeminiResponse parseGeminiResponse(String response, String filename) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            var candidates = (List<?>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return GeminiResponse.failed("No candidates in Gemini response");
            }

            var firstCandidate = (Map<String, Object>) candidates.get(0);
            var content = (Map<String, Object>) firstCandidate.get("content");
            var parts = (List<?>) content.get("parts");
            var firstPart = (Map<String, Object>) parts.get(0);
            String jsonText = (String) firstPart.get("text");

            // Clean JSON text
            jsonText = jsonText.trim();
            if (jsonText.startsWith("```json"))
                jsonText = jsonText.substring(7);
            if (jsonText.endsWith("```"))
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            jsonText = jsonText.trim();

            Map<String, Object> structuredData = objectMapper.readValue(jsonText, Map.class);

            // Extract document type
            String documentType = (String) structuredData.get("document_type");
            if (documentType == null)
                documentType = "UNKNOWN";

            // Extract confidence
            Double confidenceScore = parseConfidence(structuredData.get("confidence"));

            // Validate structured_fields exist
            var structuredFields = (List<?>) structuredData.get("structured_fields");
            String proseSummary = (String) structuredData.get("prose_summary");
            String fullText = (String) structuredData.get("full_text");

            log.info("Gemini Hybrid Extraction successful:");
            log.info("    Document type: {}", documentType);
            log.info("   Confidence: {}", String.format("%.1f%%", confidenceScore * 100));
            log.info("   Structured fields: {}", (structuredFields != null ? structuredFields.size() : 0));
            log.info("   Prose summary: {}", (proseSummary != null ? proseSummary.length() + " chars" : "MISSING"));
            log.info("   Full text: {}", (fullText != null ? fullText.length() + " chars" : "MISSING"));

            return GeminiResponse.success(structuredData, documentType, confidenceScore);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response for {}: {}", filename, e.getMessage(), e);
            return GeminiResponse.failed("Parse error: " + e.getMessage());
        }
    }

    private Double parseConfidence(Object confidenceObj) {
        if (confidenceObj == null)
            return 0.0;
        if (confidenceObj instanceof Number)
            return ((Number) confidenceObj).doubleValue();
        if (confidenceObj instanceof String) {
            try {
                return Double.parseDouble((String) confidenceObj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Response object from Gemini processing — provides access to all 3 extraction
     * outputs.
     */
    public static class GeminiResponse {
        private final Map<String, Object> structuredData;
        private final String documentType;
        private final Double confidenceScore;
        private final String errorMessage;
        private final boolean successful;

        private GeminiResponse(Map<String, Object> structuredData, String documentType, Double confidenceScore,
                String errorMessage, boolean successful) {
            this.structuredData = structuredData;
            this.documentType = documentType;
            this.confidenceScore = confidenceScore;
            this.errorMessage = errorMessage;
            this.successful = successful;
        }

        public static GeminiResponse success(Map<String, Object> structuredData, String documentType,
                Double confidenceScore) {
            return new GeminiResponse(structuredData, documentType, confidenceScore, null, true);
        }

        public static GeminiResponse failed(String errorMessage) {
            return new GeminiResponse(null, "UNKNOWN", 0.0, errorMessage, false);
        }

        // --- Core Getters ---
        public Map<String, Object> getStructuredData() {
            return structuredData;
        }

        public String getDocumentType() {
            return documentType;
        }

        public Double getConfidenceScore() {
            return confidenceScore;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isSuccessful() {
            return successful;
        }

        // --- 3 Output Accessors ---

        /** Full verbatim transcription for LLM context window */
        public String getFullText() {
            if (structuredData == null)
                return null;
            return (String) structuredData.get("full_text");
        }

        /** Clean prose summary for vector embedding in Pinecone */
        public String getProseSummary() {
            if (structuredData == null)
                return null;
            return (String) structuredData.get("prose_summary");
        }

        /** JSON array of structured key-value fields for SQL storage */
        public String getStructuredFieldsJson() {
            if (structuredData == null)
                return null;
            Object fields = structuredData.get("structured_fields");
            if (fields == null)
                return "[]";
            try {
                return new ObjectMapper().writeValueAsString(fields);
            } catch (Exception e) {
                return "[]";
            }
        }

        /**
         * For backward compatibility — returns full_text, falls back to prose_summary
         */
        public String getRawText() {
            String fullText = getFullText();
            if (fullText != null && !fullText.isEmpty())
                return fullText;
            String prose = getProseSummary();
            if (prose != null && !prose.isEmpty())
                return prose;
            // Last resort: serialize the entire JSON
            try {
                return new ObjectMapper().writeValueAsString(structuredData);
            } catch (Exception e) {
                return null;
            }
        }
    }
}

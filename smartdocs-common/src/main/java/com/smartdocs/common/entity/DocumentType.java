package com.smartdocs.common.entity;

/**
 * Standardized document type categories (9 total).
 *
 * Used across the pipeline:
 * - GeminiService normalizes AI output → enum name
 * - DocumentPipelineService stores enum name in DB
 * - RoutingLLMService uses enum names in its classification prompt
 * - DocumentRepository matches on exact enum names (no LOWER/LIKE needed)
 *
 * To add new types later, add the enum value + aliases in fromGeminiString().
 */
public enum DocumentType {

    IDENTITY_DOCUMENT, // Aadhaar, PAN, Passport, Driving License, Voter ID
    EDUCATION_DOCUMENT, // Marksheet, Degree, Certificate, Report Card
    FINANCIAL_BILL, // Invoice, Receipt, Bill, Utility Bill, Phone Bill
    BANK_STATEMENT, // Bank Statement, Passbook
    SALARY_SLIP, // Salary Slip, Pay Stub, Payslip
    LEGAL_DOCUMENT, // Contract, Agreement, Insurance Policy, Will, Rental Agreement
    GOVERNMENT_DOCUMENT, // Tax Return, ITR, Form 16, Property Tax, Registration Certificate
    MEDICAL_DOCUMENT, // Prescription, Lab Report, Discharge Summary, Health Record
    OTHER; // Anything not recognized

    /**
     * Normalize the free-text document type string returned by Gemini
     * into a standardized enum value.
     *
     * Handles common variations, typos, and formatting differences.
     * Example: "Aadhar Card" → IDENTITY_DOCUMENT
     * "Tax Invoice" → FINANCIAL_BILL
     * "Rental Agreement" → LEGAL_DOCUMENT
     * "Income Tax Return" → GOVERNMENT_DOCUMENT
     */
    public static DocumentType fromGeminiString(String raw) {
        if (raw == null || raw.isBlank())
            return OTHER;

        String normalized = raw.trim().toLowerCase()
                .replace("-", " ")
                .replace("_", " ");

        // Identity documents
        if (normalized.contains("aadhaar") || normalized.contains("aadhar")
                || normalized.contains("pan card") || normalized.contains("pan ")
                || normalized.contains("passport")
                || normalized.contains("driving license") || normalized.contains("driving licence")
                || normalized.contains("voter id") || normalized.contains("election card")
                || normalized.contains("identity") || normalized.contains("id card")
                || normalized.contains("ration card")) {
            return IDENTITY_DOCUMENT;
        }

        // Education documents
        if (normalized.contains("marksheet") || normalized.contains("mark sheet")
                || normalized.contains("report card")
                || normalized.contains("certificate") || normalized.contains("degree")
                || normalized.contains("diploma") || normalized.contains("transcript")
                || normalized.contains("education") || normalized.contains("academic")) {
            return EDUCATION_DOCUMENT;
        }

        // Legal documents (contracts, policies, agreements)
        if (normalized.contains("contract") || normalized.contains("agreement")
                || normalized.contains("insurance") || normalized.contains("policy")
                || normalized.contains("will") || normalized.contains("affidavit")
                || normalized.contains("power of attorney") || normalized.contains("rental")
                || normalized.contains("lease") || normalized.contains("legal")
                || normalized.contains("nda") || normalized.contains("mou")) {
            return LEGAL_DOCUMENT;
        }

        // Government documents (tax filings, registrations)
        if (normalized.contains("tax return") || normalized.contains("itr")
                || normalized.contains("form 16") || normalized.contains("form16")
                || normalized.contains("property tax") || normalized.contains("gst filing")
                || normalized.contains("registration certificate") || normalized.contains("govt")
                || normalized.contains("government") || normalized.contains("challan")
                || normalized.contains("tds") || normalized.contains("income tax")) {
            return GOVERNMENT_DOCUMENT;
        }

        // Medical documents
        if (normalized.contains("medical") || normalized.contains("prescription")
                || normalized.contains("lab report") || normalized.contains("pathology")
                || normalized.contains("discharge") || normalized.contains("diagnosis")
                || normalized.contains("health") || normalized.contains("doctor")
                || normalized.contains("hospital") || normalized.contains("clinical")) {
            return MEDICAL_DOCUMENT;
        }

        // Financial bills (money spent)
        if (normalized.contains("invoice") || normalized.contains("receipt")
                || normalized.contains("bill") || normalized.contains("utility")
                || normalized.contains("electricity") || normalized.contains("water")
                || normalized.contains("phone") || normalized.contains("mobile")
                || normalized.contains("recharge") || normalized.contains("grocery")
                || normalized.contains("purchase") || normalized.contains("order")) {
            return FINANCIAL_BILL;
        }

        // Bank statements (money in/out)
        if (normalized.contains("bank statement") || normalized.contains("passbook")
                || normalized.contains("account statement") || normalized.contains("bank")) {
            return BANK_STATEMENT;
        }

        // Salary slips (income)
        if (normalized.contains("salary") || normalized.contains("pay slip")
                || normalized.contains("payslip") || normalized.contains("pay stub")
                || normalized.contains("wage") || normalized.contains("compensation")) {
            return SALARY_SLIP;
        }

        return OTHER;
    }
}

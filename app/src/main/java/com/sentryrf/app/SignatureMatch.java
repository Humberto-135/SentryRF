package com.sentryrf.app;

final class SignatureMatch {
    final String signatureId;
    final String category;
    final String label;
    final int risk;
    final String confidence;

    SignatureMatch(String signatureId, String category, String label, int risk, String confidence) {
        this.signatureId = signatureId;
        this.category = category;
        this.label = label;
        this.risk = risk;
        this.confidence = confidence;
    }

    static SignatureMatch generic() {
        return new SignatureMatch("generic", "OTHER", "Unclassified radio source", 8, "LOW");
    }
}

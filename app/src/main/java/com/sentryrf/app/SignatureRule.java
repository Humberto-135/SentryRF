package com.sentryrf.app;

import java.util.regex.Pattern;

final class SignatureRule {
    final String id;
    final String radio;
    final String category;
    final String label;
    final int risk;
    final String confidence;
    final Pattern namePattern;
    final Pattern ssidPattern;
    final String serviceUuid;
    final Integer manufacturerId;

    SignatureRule(String id, String radio, String category, String label, int risk, String confidence,
                  Pattern namePattern, Pattern ssidPattern, String serviceUuid, Integer manufacturerId) {
        this.id = id;
        this.radio = radio;
        this.category = category;
        this.label = label;
        this.risk = risk;
        this.confidence = confidence;
        this.namePattern = namePattern;
        this.ssidPattern = ssidPattern;
        this.serviceUuid = serviceUuid;
        this.manufacturerId = manufacturerId;
    }
}

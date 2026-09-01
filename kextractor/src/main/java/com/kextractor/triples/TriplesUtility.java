package com.kextractor.triples;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TriplesUtility {

    public static String mapToSchemaType(String nerType) {
        if (nerType == null) {
            return "Thing";
        }
        switch (nerType.toUpperCase()) {
            case "PERSON":
                return "Person";
            case "ORGANIZATION":
                return "Organization";
            case "LOCATION":
            case "COUNTRY":
            case "CITY":
            case "STATE_OR_PROVINCE":
                return "Place";
            default:
                return "Thing";
        }
    }

    public static String sanitizeForURI(String text) {

        if (text == null) {
            return "";
        }
        String cleanText = text.toLowerCase().replaceAll("\\s+", "_");
        return URLEncoder.encode(cleanText, StandardCharsets.UTF_8);
    }

}

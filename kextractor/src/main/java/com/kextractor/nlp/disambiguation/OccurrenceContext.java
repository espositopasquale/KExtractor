package com.kextractor.nlp.disambiguation;

import com.fasterxml.jackson.databind.JsonNode;

public record OccurrenceContext(String text, int begin, int end) {

    public OccurrenceContext {
        if (text == null || begin < 0 || end <= begin || end > text.length()) {
            throw new IllegalArgumentException("Invalid WSD target span");
        }
        if (text.substring(begin, end).isBlank()) {
            throw new IllegalArgumentException("Empty target text");
        }
        if (text.contains("<t>") || text.contains("</t>")) {
            throw new IllegalArgumentException("Original sentence contains reserved WSD target markers");
        }
        if (splitsSurrogate(text, begin) || splitsSurrogate(text, end)) {
            throw new IllegalArgumentException("WSD target splits a Unicode code point");
        }
    }

    public static OccurrenceContext of(String text, int begin, int end) {
        return new OccurrenceContext(text, begin, end);
    }

    public String markedText() {
        return text.substring(0, begin) + "<t>" + text.substring(begin, end) + "</t>" + text.substring(end);
    }

    public String surface() {
        return text.substring(begin, end);
    }

    public static OccurrenceContext from(String document, JsonNode sentence, int tokenIndex) {
        JsonNode tokens = sentence.path("tokens");
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Empty sentence for WSD");
        }
        int start = tokens.get(0).path("characterOffsetBegin").asInt(-1);
        int finish = tokens.get(tokens.size() - 1).path("characterOffsetEnd").asInt(-1);
        if (start < 0 || finish < start || finish > document.length()) {
            throw new IllegalArgumentException("Invalid sentence offsets");
        }
        for (JsonNode token : tokens) {
            if (token.path("index").asInt() == tokenIndex) {
                return of(document.substring(start, finish), token.path("characterOffsetBegin").asInt(-1) - start,
                        token.path("characterOffsetEnd").asInt(-1) - start);
            }
        }
        throw new IllegalArgumentException("Missing WSD token " + tokenIndex);
    }

    private static boolean splitsSurrogate(String text, int index) {
        return index > 0 && index < text.length() && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index));
    }
}

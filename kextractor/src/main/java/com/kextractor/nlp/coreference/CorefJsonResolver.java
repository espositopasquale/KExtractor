package com.kextractor.nlp.coreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.kextractor.utils.Replacement;

public class CorefJsonResolver {

    private static final Set<String> PLURAL_PRONOUNS = Set.of(
            "they", "them", "their", "theirs", "themselves");

    public static String resolveCoreferencesFromJson(
            String originalText,
            JsonNode root,
            boolean pronounsOnly) {

        if (originalText == null || root == null || !root.has("corefs")) {
            return originalText;
        }

        List<Replacement> replacements = new ArrayList<>();

        JsonNode corefs = root.get("corefs");

        for (Map.Entry<String, JsonNode> entry : corefs.properties()) {

            JsonNode chain = entry.getValue();

            JsonNode representative = null;

            for (JsonNode mention : chain) {
                if (mention.path("isRepresentativeMention").asBoolean(false)) {
                    representative = mention;
                    break;
                }
            }

            if (representative == null) {
                continue;
            }

            String repText = representative.path("text").asText("").trim();
            int repId = representative.path("id").asInt(-1);

            if (repText.isEmpty()) {
                continue;
            }

            for (JsonNode mention : chain) {

                if (mention.path("id").asInt(-1) == repId) {
                    continue;
                }

                String mentionType = mention.path("type").asText("");
                String mentionText = mention.path("text").asText("").trim();

                if (mentionText.isEmpty()) {
                    continue;
                }

                if (mentionText.equalsIgnoreCase(repText)) {
                    continue;
                }

                if ("PROPER".equals(mentionType)) {
                    continue;
                }

                if (pronounsOnly && !"PRONOMINAL".equals(mentionType)) {
                    continue;
                }

                if (PLURAL_PRONOUNS.contains(mentionText.toLowerCase())) {
                    continue;
                }

                int sentIndex = mention.path("sentNum").asInt(1) - 1;
                int startIdx = mention.path("startIndex").asInt(1) - 1;
                int endIdx = mention.path("endIndex").asInt(1) - 2;

                JsonNode sentences = root.path("sentences");

                if (sentIndex < 0 || sentIndex >= sentences.size()) {
                    continue;
                }

                JsonNode tokens = sentences.get(sentIndex).path("tokens");

                if (startIdx < 0
                        || endIdx < startIdx
                        || endIdx >= tokens.size()) {
                    continue;
                }

                int beginChar = tokens.get(startIdx)
                        .path("characterOffsetBegin")
                        .asInt();

                int endChar = tokens.get(endIdx)
                        .path("characterOffsetEnd")
                        .asInt();

                replacements.add(new Replacement(beginChar, endChar, repText));
            }
        }

        replacements.sort(
                Comparator.comparingInt((Replacement r) -> r.start)
                        .reversed());

        StringBuilder sb = new StringBuilder(originalText);

        int previousStart = Integer.MAX_VALUE;

        for (Replacement r : replacements) {

            if (r.end > previousStart) {
                continue;
            }

            sb.replace(r.start, r.end, r.text);
            previousStart = r.start;
        }

        return sb.toString();
    }
}

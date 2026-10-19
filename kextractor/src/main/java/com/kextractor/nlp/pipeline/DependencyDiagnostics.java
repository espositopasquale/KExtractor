package com.kextractor.nlp.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Optional root-dependency summary, independent of semantic graph construction. */
public final class DependencyDiagnostics {
    private DependencyDiagnostics() { }

    public static Map<String, Object> rootVerbGraph(JsonNode sentence) {
        JsonNode dependencies = sentence.has("enhancedPlusPlusDependencies")
                ? sentence.path("enhancedPlusPlusDependencies") : sentence.path("basicDependencies");
        JsonNode root = null;
        for (JsonNode edge : dependencies) if (edge.path("dep").asText().equals("ROOT")) { root = edge; break; }
        if (root == null) return null;
        Map<Integer, JsonNode> tokens = new HashMap<>();
        for (JsonNode token : sentence.path("tokens")) tokens.put(token.path("index").asInt(), token);
        int index = root.path("dependent").asInt();
        JsonNode token = tokens.getOrDefault(index, root);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rootVerb", token.path("word").asText(root.path("dependentGloss").asText()));
        result.put("rootLemma", token.path("lemma").asText(root.path("dependentGloss").asText()));
        result.put("rootIndex", index);
        List<JsonNode> outgoing = new ArrayList<>();
        for (JsonNode edge : dependencies) if (edge.path("governor").asInt() == index) outgoing.add(edge);
        outgoing.sort(Comparator.comparingInt(e -> e.path("dependent").asInt()));
        List<Map<String, Object>> arguments = new ArrayList<>();
        for (JsonNode edge : outgoing) {
            int dependent = edge.path("dependent").asInt();
            JsonNode argument = tokens.getOrDefault(dependent, edge);
            arguments.add(Map.of("relationship", edge.path("dep").asText(),
                    "argumentWord", argument.path("word").asText(edge.path("dependentGloss").asText()),
                    "argumentIndex", dependent, "argumentPOS", argument.path("pos").asText()));
        }
        result.put("arguments", arguments);
        return result;
    }
}

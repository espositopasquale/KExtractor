package com.kextractor.nlp.pipeline.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.nlp.coreference.CorefJsonResolver;
import com.kextractor.nlp.pipeline.DependencyDiagnostics;
import com.kextractor.triples.GraphSerializer;
import com.kextractor.triples.semantic.SemanticGraphBuilder;
import com.kextractor.utils.GraphFormat;

public class ServerPipeline {

    public static HashMap<String, Object> pipelineServerMain(String text, String graphFormat, boolean fullAnalysis)
            throws Exception {
        String jsonFromServer = CoreNLPClient.analyzeText(text);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonFromServer);

        HashMap<String, Object> result = new HashMap<>();
        result.put("originalText", text);

        // 1. Extract tokens
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> tokens = new ArrayList<>();
            if (root.has("sentences")) {
                root.get("sentences").forEach(sent -> {
                    if (sent.has("tokens")) {
                        sent.get("tokens").forEach(t -> {
                            HashMap<String, Object> map = new HashMap<>();
                            map.put("word", t.get("word").asText());
                            map.put("lemma", t.get("lemma").asText());
                            map.put("pos", t.get("pos").asText());
                            map.put("ner", t.get("ner").asText());
                            map.put("begin", t.get("characterOffsetBegin").asInt());
                            map.put("end", t.get("characterOffsetEnd").asInt());
                            tokens.add(map);
                        });
                    }
                });
            }
            result.put("tokens", tokens);
        }

        // 2. Extract named entities 
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> entities = new ArrayList<>();
            for (JsonNode sentence : root.path("sentences")) {
                for (JsonNode em : sentence.path("entitymentions")) {
                    HashMap<String, Object> e = new HashMap<>();
                    e.put("text", em.path("text").asText());
                    e.put("type", em.path("ner").asText());
                    entities.add(e);
                }
            }
            result.put("entities", entities);
        }

        // 3. Extract sentences and dependency parse information
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> sentences = new ArrayList<>();
            if (root.has("sentences")) {
                for (JsonNode sent : root.get("sentences")) {
                    HashMap<String, Object> s = new HashMap<>();
                    s.put("id", sent.get("index").asInt() + 1);
                    s.put("rootVerbKnowledgeGraph", DependencyDiagnostics.rootVerbGraph(sent));
                    sentences.add(s);
                }
            }
            result.put("sentences", sentences);
        }

        // 4. Extract coreference chains
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> corefs = new ArrayList<>();
            if (root.has("corefs")) {
                for (Map.Entry<String, JsonNode> entry : root.get("corefs").properties()) {
                    JsonNode chain = entry.getValue();
                    if (chain.isArray()) {
                        HashMap<String, Object> c = new HashMap<>();
                        c.put("id", entry.getKey());
                        String representative = "";
                        ArrayList<HashMap<String, Object>> mentions = new ArrayList<>();
                        for (JsonNode m : chain) {
                            if (m.path("isRepresentativeMention").asBoolean(false)) {
                                representative = m.path("text").asText("");
                            }
                            HashMap<String, Object> mm = new HashMap<>();
                            mm.put("text", m.path("text").asText(""));
                            mm.put("sentence", m.path("sentNum").asInt(0));
                            mm.put("startIndex", m.path("startIndex").asInt(0));
                            mm.put("endIndex", m.path("endIndex").asInt(0));
                            mentions.add(mm);
                        }
                        c.put("representative", representative);
                        c.put("mentions", mentions);
                        corefs.add(c);
                    }
                }
            }
            result.put("coreferences", corefs);
        }

        // Optional OpenIE diagnostics; the final graph always uses original-text annotations.
        if (fullAnalysis) {
            // 5. Resolve coreferences locally using the JSON character offsets
            String resolvedText = CorefJsonResolver.resolveCoreferencesFromJson(text, root, false);
            result.put("resolvedText", resolvedText);

            // 6. Extract diagnostic OpenIE triples
            String resolvedJson = resolvedText.equals(text) ? jsonFromServer : CoreNLPClient.analyzeText(resolvedText);
            JsonNode resolvedRoot = mapper.readTree(resolvedJson);

            ArrayList<HashMap<String, Object>> relations = new ArrayList<>();
            if (resolvedRoot.has("sentences")) {
                for (JsonNode sent : resolvedRoot.get("sentences")) {
                    if (sent.has("openie")) {
                        for (JsonNode triple : sent.get("openie")) {
                            HashMap<String, Object> r = new HashMap<>();
                            r.put("subject", triple.get("subject").asText());
                            r.put("relation", triple.get("relation").asText());
                            r.put("object", triple.get("object").asText());
                            r.put("confidence", triple.has("confidence") ? triple.get("confidence").asDouble() : 1.0);
                            relations.add(r);
                        }
                    }
                }
            }
            result.put("triples", relations);

        }

        Map<String, Object> semanticGraph = SemanticGraphBuilder.build(text, root);

        GraphFormat format = GraphFormat.fromString(graphFormat);
        if (format == GraphFormat.JSONLD) {
            result.put("rdfKnowledgeGraph", semanticGraph);
        } else {
            String serialized = GraphSerializer.serialize(semanticGraph, format);
            result.put("rdfKnowledgeGraph", serialized);
        }

        return result;
    }

}

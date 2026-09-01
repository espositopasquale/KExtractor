package com.kextractor.nlp.pipeline.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.nlp.coreference.CorefJsonResolver;
import com.kextractor.triples.GraphSerializer;
import com.kextractor.triples.schema.SchemaGen;
import com.kextractor.utils.EntityMetadata;
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
        Map<String, EntityMetadata> entityRegistry = new HashMap<>();
        ArrayList<HashMap<String, Object>> entities = new ArrayList<>();
        if (root.has("sentences")) {
            for (JsonNode sentence : root.get("sentences")) {
                if (sentence.has("entitymentions")) {
                    for (JsonNode em : sentence.get("entitymentions")) {
                        String emText = em.get("text").asText();
                        String emType = em.get("ner").asText();
                        entityRegistry.put(emText.toLowerCase(), new EntityMetadata(emText, emType));
                        if (fullAnalysis) {
                            HashMap<String, Object> e = new HashMap<>();
                            e.put("text", emText);
                            e.put("type", emType);
                            entities.add(e);
                        }
                    }
                }
            }
        }
        if (fullAnalysis) {
            result.put("entities", entities);
        }

        // 3. Extract sentences and dependency parse information
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> sentences = new ArrayList<>();
            if (root.has("sentences")) {
                for (JsonNode sent : root.get("sentences")) {
                    HashMap<String, Object> s = new HashMap<>();
                    s.put("id", sent.get("index").asInt() + 1);
                    HashMap<String, Object> rootGraph = new HashMap<>();
                    JsonNode deps = sent.has("enhancedPlusPlusDependencies")
                            ? sent.get("enhancedPlusPlusDependencies")
                            : sent.get("basicDependencies");
                    if (deps != null) {
                        JsonNode rootNode = null;
                        for (JsonNode edge : deps) {
                            if ("ROOT".equals(edge.get("dep").asText())) {
                                rootNode = edge;
                                break;
                            }
                        }
                        if (rootNode != null) {
                            int rootGovIndex = rootNode.get("dependent").asInt();
                            rootGraph.put("rootVerb", rootNode.get("dependentGloss").asText());
                            rootGraph.put("rootIndex", rootGovIndex);
                            ArrayList<HashMap<String, Object>> arguments = new ArrayList<>();
                            for (JsonNode edge : deps) {
                                if (edge.get("governor").asInt() == rootGovIndex) {
                                    HashMap<String, Object> arg = new HashMap<>();
                                    arg.put("relationship", edge.get("dep").asText());
                                    arg.put("argumentWord", edge.get("dependentGloss").asText());
                                    arg.put("argumentIndex", edge.get("dependent").asInt());
                                    arguments.add(arg);
                                }
                            }
                            rootGraph.put("arguments", arguments);
                        }
                    }
                    s.put("rootVerbKnowledgeGraph", rootGraph.isEmpty() ? null : rootGraph);
                    sentences.add(s);
                }
            }
            result.put("sentences", sentences);
        }

        // 4. Extract coreference chains
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> corefs = new ArrayList<>();
            if (root != null && root.has("corefs")) {
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

        // 5. Resolve coreferences locally using the JSON character offsets
        String resolvedText = CorefJsonResolver.resolveCoreferencesFromJson(text, root, false);
        if (fullAnalysis) {
            result.put("resolvedText", resolvedText);
        }

        // 6. Extract OpenIE triples and generate the Schema.org RDF graph
        String resolvedJson = CoreNLPClient.analyzeText(resolvedText);
        JsonNode resolvedRoot = mapper.readTree(resolvedJson);

        ArrayList<HashMap<String, Object>> relations = new ArrayList<>();
        List<HashMap<String, Object>> collectedTriples = new ArrayList<>();
        if (resolvedRoot.has("sentences")) {
            for (JsonNode sent : resolvedRoot.get("sentences")) {
                if (sent.has("openie")) {
                    for (JsonNode triple : sent.get("openie")) {
                        String rawSub = triple.get("subject").asText();
                        String rawRel = triple.get("relation").asText();
                        String rawObj = triple.get("object").asText();
                        String alignedSubject = resolveServerToken(rawSub, entityRegistry);
                        String alignedObject = resolveServerToken(rawObj, entityRegistry);
                        HashMap<String, Object> r = new HashMap<>();
                        r.put("subject", alignedSubject);
                        r.put("relation", rawRel);
                        r.put("object", alignedObject);
                        r.put("confidence", triple.has("confidence") ? triple.get("confidence").asDouble() : 1.0);
                        collectedTriples.add(r);
                        if (fullAnalysis) {
                            relations.add(r);
                        }
                    }
                }
            }
        }
        if (fullAnalysis) {
            result.put("triples", relations);
        }

        Map<String, Object> schemaRdfGraph = SchemaGen.generateServerGraph(collectedTriples, entityRegistry);

        GraphFormat format = GraphFormat.fromString(graphFormat);
        if (format == GraphFormat.JSONLD) {
            result.put("rdfKnowledgeGraph", schemaRdfGraph);
        } else {
            String serialized = GraphSerializer.serialize(schemaRdfGraph, format);
            result.put("rdfKnowledgeGraph", serialized);
        }

        return result;
    }

    private static String resolveServerToken(String token, Map<String, EntityMetadata> registry) {
        if (token == null)
            return "";
        String cleanToken = token.toLowerCase().trim();

        if (cleanToken.equals("there") || cleanToken.equals("here")) {
            return findRegistryEntity(registry, List.of("CITY", "COUNTRY", "LOCATION", "STATE_OR_PROVINCE"))
                    .orElse(token);
        }

        if (cleanToken.equals("it") || cleanToken.equals("its")) {
            return findRegistryEntity(registry, List.of("ORGANIZATION", "MISC")).orElse(token);
        }
        if (cleanToken.equals("he") || cleanToken.equals("she") || cleanToken.equals("they")
                || cleanToken.equals("who")) {
            return findRegistryEntity(registry, List.of("PERSON")).orElse(token);
        }
        return token;
    }

    private static Optional<String> findRegistryEntity(Map<String, EntityMetadata> registry, List<String> targetTypes) {
        return registry.values().stream()
                .filter(meta -> meta.type != null && targetTypes.contains(meta.type.toUpperCase()))
                .map(meta -> (String) meta.label)
                .findFirst();
    }
}
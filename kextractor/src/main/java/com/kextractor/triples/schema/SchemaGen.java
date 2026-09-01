package com.kextractor.triples.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kextractor.triples.TriplesUtility;
import com.kextractor.utils.EntityMetadata;
import com.kextractor.utils.Utility;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

public class SchemaGen {

    private static final Map<String, String> SCHEMA_TYPE_MAP = new HashMap<>();
    private static final Map<String, String> SCHEMA_PROPERTY_MAP = new HashMap<>();

    private static final Set<String> LITERAL_TYPES = Set.of(
            "TIME",
            "DATE",
            "DURATION",
            "NUMBER",
            "MONEY",
            "PERCENT",
            "ORDINAL",
            "SET");

    private static final Set<String> COPULA_RELATIONS = Set.of("be", "was", "is", "has", "had");

    static {
        SCHEMA_TYPE_MAP.put("PERSON", "Person");
        SCHEMA_TYPE_MAP.put("TITLE", "JobTitle");
        SCHEMA_TYPE_MAP.put("ORGANIZATION", "Organization");
        SCHEMA_TYPE_MAP.put("LOCATION", "Place");
        SCHEMA_TYPE_MAP.put("CITY", "City");
        SCHEMA_TYPE_MAP.put("STATE_OR_PROVINCE", "State");
        SCHEMA_TYPE_MAP.put("COUNTRY", "Country");
        SCHEMA_TYPE_MAP.put("DURATION", "Duration");
        SCHEMA_TYPE_MAP.put("SET", "Schedule");
        SCHEMA_TYPE_MAP.put("MONEY", "MonetaryAmount");
        SCHEMA_TYPE_MAP.put("PERCENT", "QuantitativeValue");
        SCHEMA_TYPE_MAP.put("EMAIL", "ContactPoint");
        SCHEMA_TYPE_MAP.put("URL", "URL");
        SCHEMA_TYPE_MAP.put("HANDLE", "PropertyValue");
        SCHEMA_TYPE_MAP.put("NATIONALITY", "Text");
        SCHEMA_TYPE_MAP.put("RELIGION", "Text");
        SCHEMA_TYPE_MAP.put("IDEOLOGY", "Text");
        SCHEMA_TYPE_MAP.put("CRIMINAL_CHARGE", "Text");
        SCHEMA_TYPE_MAP.put("CAUSE_OF_DEATH", "Text");
        SCHEMA_TYPE_MAP.put("MISC", "Thing");

        SCHEMA_PROPERTY_MAP.put("bornIn", "birthPlace");
        SCHEMA_PROPERTY_MAP.put("livedIn", "homeLocation");
        SCHEMA_PROPERTY_MAP.put("worksFor", "worksFor");
        SCHEMA_PROPERTY_MAP.put("employedBy", "worksFor");
        SCHEMA_PROPERTY_MAP.put("locatedIn", "containedInPlace");
        SCHEMA_PROPERTY_MAP.put("isCapitalOf", "containedInPlace");
        SCHEMA_PROPERTY_MAP.put("partOf", "isPartOf");
        SCHEMA_PROPERTY_MAP.put("wrote", "author");
        SCHEMA_PROPERTY_MAP.put("authored", "author");
        SCHEMA_PROPERTY_MAP.put("studyAt", "alumniOf");
        SCHEMA_PROPERTY_MAP.put("studiedAt", "alumniOf");
    }

    public static Map<String, Object> generateGraph(
            List<RelationTriple> rawTriples,
            Map<String, EntityMetadata> entityRegistry,
            edu.stanford.nlp.pipeline.CoreDocument originalDoc) {
        Map<String, Map<String, Object>> entityHub = new LinkedHashMap<>();
        for (RelationTriple triple : rawTriples) {
            String sub = triple.subjectGloss();
            String rel = triple.relationGloss();
            String obj = triple.objectGloss();
            processTriple(sub, rel, obj, entityRegistry, entityHub);
        }
        return buildJsonLdContainer(entityHub);
    }

    public static Map<String, Object> generateServerGraph(
            List<HashMap<String, Object>> rawTriples,
            Map<String, EntityMetadata> entityRegistry) {
        Map<String, Map<String, Object>> entityHub = new LinkedHashMap<>();
        for (HashMap<String, Object> triple : rawTriples) {
            String sub = (String) triple.get("subject");
            String rel = (String) triple.get("relation");
            String obj = (String) triple.get("object");
            if (sub == null || rel == null || obj == null) {
                continue;
            }
            processTriple(sub, rel, obj, entityRegistry, entityHub);
        }
        return buildJsonLdContainer(entityHub);
    }

    private static void processTriple(
            String sub, String rel, String obj,
            Map<String, EntityMetadata> entityRegistry,
            Map<String, Map<String, Object>> entityHub) {

        String lowerRel = rel.toLowerCase().trim();
        if (COPULA_RELATIONS.contains(lowerRel)) {
            return;
        }

        String subId = getOrCreateEntity(sub, entityRegistry, entityHub);
        Map<String, Object> subjectNode = entityHub.get(subId);

        String normalizedRel = Utility.toLowerCamelCase(rel);
        String predicateKey;
        if (SCHEMA_PROPERTY_MAP.containsKey(normalizedRel)) {
            predicateKey = "schema:" + SCHEMA_PROPERTY_MAP.get(normalizedRel);
        } else {
            String dynamicMatch = SchemaPropertyMatcher.matchVerbalPart(rel);
            predicateKey = dynamicMatch != null ? "schema:" + dynamicMatch : "kex:" + normalizedRel;
        }

        Object targetValue = resolveTargetValue(obj, entityRegistry, entityHub);

        addPredicateValue(subjectNode, predicateKey, targetValue);
    }

    private static boolean isYear(String value) {
        return value.matches("^\\d{4}$");
    }

    private static Object resolveTargetValue(
            String obj,
            Map<String, EntityMetadata> entityRegistry,
            Map<String, Map<String, Object>> entityHub) {

        EntityMetadata meta = entityRegistry.get(normalizeKey(obj));
        String metaType = (meta != null && meta.type != null)
                ? meta.type.toUpperCase()
                : null;

        
        if (meta != null && !LITERAL_TYPES.contains(metaType)) {

            String objId = getOrCreateEntity(
                    obj,
                    entityRegistry,
                    entityHub);

            Map<String, String> ref = new LinkedHashMap<>();
            ref.put("@id", (String) entityHub.get(objId).get("@id"));

            return ref;
        }

        
        Map<String, String> typedLiteral = new LinkedHashMap<>();
        typedLiteral.put("@value", obj);

        if (isYear(obj)) {
            typedLiteral.put("@type", "xsd:gYear");

        } else if ("TIME".equals(metaType)
                || obj.matches("(?i).*\\b\\d{1,2}:\\d{2}\\s*(am|pm)?\\b.*")) {

            typedLiteral.put("@type", "xsd:time");

        } else if ("DATE".equals(metaType)) {

            typedLiteral.put("@type", "xsd:date");

        } else if ("NUMBER".equals(metaType)
                || "PERCENT".equals(metaType)) {

            typedLiteral.put("@type", "xsd:decimal");

        } else if ("ORDINAL".equals(metaType)) {

            typedLiteral.put("@type", "xsd:integer");

        } else {

            typedLiteral.put("@type", "xsd:string");
        }

        return typedLiteral;
    }

    @SuppressWarnings("unchecked")
    private static void addPredicateValue(
            Map<String, Object> subjectNode, String predicateKey, Object targetValue) {
        Object existingValue = subjectNode.get(predicateKey);
        if (existingValue == null) {
            subjectNode.put(predicateKey, targetValue);
            return;
        }

        List<Object> valueList;
        if (existingValue instanceof List) {
            valueList = (List<Object>) existingValue;
        } else {
            valueList = new ArrayList<>();
            valueList.add(existingValue);
            subjectNode.put(predicateKey, valueList);
        }

        if (!valueList.contains(targetValue)) {
            valueList.add(targetValue);
        }
    }

    private static String normalizeKey(String name) {
        return name.toLowerCase().trim();
    }

    private static String getOrCreateEntity(
            String name,
            Map<String, EntityMetadata> registry,
            Map<String, Map<String, Object>> hub) {
        String key = normalizeKey(name);
        if (!hub.containsKey(key)) {
            Map<String, Object> node = new LinkedHashMap<>();
            EntityMetadata meta = registry.get(key);
            if (meta != null) {
                node.put("@id", "kex:" + TriplesUtility.sanitizeForURI(name));
                String nerType = meta.type != null ? meta.type.toUpperCase() : "MISC";
                String schemaType = SCHEMA_TYPE_MAP.getOrDefault(nerType, "Thing");
                node.put("@type", "schema:" + schemaType);
            } else {
                node.put("@id", "kex:" + TriplesUtility.sanitizeForURI(name));
                node.put("@type", "schema:Thing");
            }
            node.put("name", name);
            hub.put(key, node);
        }
        return key;
    }

    private static Map<String, Object> buildJsonLdContainer(Map<String, Map<String, Object>> entityHub) {
        Map<String, Object> finalEnvelope = new LinkedHashMap<>();
        Map<String, String> contextMap = new LinkedHashMap<>();
        contextMap.put("schema", "https://schema.org/");
        contextMap.put("kex", "http://kextractor.com/entity/");
        contextMap.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        finalEnvelope.put("@context", contextMap);
        finalEnvelope.put("@graph", new ArrayList<>(entityHub.values()));
        return finalEnvelope;
    }

    public static HashMap<String, Object> buildRootVerbGraph(SemanticGraph dependencies) {
        HashMap<String, Object> verbGraph = new LinkedHashMap<>();
        IndexedWord root = dependencies.getFirstRoot();
        if (root == null) {
            return null;
        }
        verbGraph.put("rootVerb", root.word());
        verbGraph.put("rootLemma", root.lemma());
        verbGraph.put("rootIndex", root.index());

        ArrayList<HashMap<String, Object>> arguments = new ArrayList<>();
        for (SemanticGraphEdge edge : dependencies.getOutEdgesSorted(root)) {
            HashMap<String, Object> argument = new LinkedHashMap<>();
            argument.put("relationship", edge.getRelation().toString());
            argument.put("argumentWord", edge.getTarget().word());
            argument.put("argumentIndex", edge.getTarget().index());
            argument.put("argumentPOS", edge.getTarget().tag());
            arguments.add(argument);
        }
        verbGraph.put("arguments", arguments);
        return verbGraph;
    }
}
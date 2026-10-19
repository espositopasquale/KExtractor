package com.kextractor.triples.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.kextractor.nlp.disambiguation.LexicalDisambiguation;
import com.kextractor.nlp.disambiguation.OccurrenceContext;
import static com.kextractor.triples.semantic.SentenceStructure.baseRelation;
import static com.kextractor.triples.semantic.SentenceStructure.dependent;
import static com.kextractor.triples.semantic.SentenceStructure.governor;
import static com.kextractor.triples.semantic.SentenceStructure.relation;

public final class SemanticGraphBuilder {

    private SemanticGraphBuilder() {
    }

    public static Map<String, Object> build(String text, JsonNode annotation) {
        try (var wsd = LexicalDisambiguation.configured()) {
            return build(text, annotation, wsd);
        }
    }

    public static Map<String, Object> build(String text, JsonNode annotation, LexicalDisambiguation wsd) {
        return new DocumentBuilder(text, annotation, Objects.requireNonNull(wsd)).build();
    }

    private static final class DocumentBuilder {

        private final String text;
        private final JsonNode annotation;
        private final LexicalDisambiguation wsd;
        private final String document;
        private final List<Map<String, Object>> nodes = new ArrayList<>();
        private final Map<String, Map<String, Object>> entities = new LinkedHashMap<>();
        private final Map<String, Coreference> corefs;

        DocumentBuilder(String text, JsonNode annotation, LexicalDisambiguation wsd) {
            this.text = text;
            this.annotation = annotation;
            this.wsd = wsd;
            document = "kex:doc_" + hash(text);
            corefs = coreferences(annotation);
        }

        Map<String, Object> build() {
            var doc = node(document, "schema:DigitalDocument");
            doc.put("schema:text", text);
            doc.put("kex:contentHash", hash(text));
            doc.put("kex:wordnetEnabled", wsd.enabled());
            doc.put("kex:extractionMethod", "verb-centered-dependency-frames-v2");
            doc.put("kex:verbnetEnabled", VerbNetRoles.index() != null);
            doc.put("kex:assertionPolicy", "Frames describe text; embedded, modal and negative clauses are not unconditional world facts");
            if (VerbNetRoles.index() != null) {
                doc.put("kex:verbnetClassCount", VerbNetRoles.index().classCount());
            }
            nodes.add(doc);
            int ordinal = 0;
            for (JsonNode sentence : annotation.path("sentences")) {
                new SentenceBuilder(sentence, ++ordinal).build();
            }
            nodes.addAll(entities.values());
            return Map.of("@context", Map.of("schema", "https://schema.org/", "kex", "http://kextractor.com/entity/",
                    "prov", "http://www.w3.org/ns/prov#", "xsd", "http://www.w3.org/2001/XMLSchema#"), "@graph", nodes);
        }

        private final class SentenceBuilder {

            private final JsonNode sentence;
            private final int ordinal;
            private final SentenceStructure syntax;
            private final String chunkId;
            private final Map<Integer, Map<String, Object>> frames = new LinkedHashMap<>();
            private final Map<Integer, LexicalDisambiguation.Result> senses = new HashMap<>();
            private Map<String, Object> chunk;
            private int begin, end;

            SentenceBuilder(JsonNode sentence, int ordinal) {
                this.sentence = sentence;
                this.ordinal = ordinal;
                syntax = new SentenceStructure(sentence);
                chunkId = document + "_s" + ordinal;
            }

            void build() {
                if (syntax.tokens.isEmpty()) {
                    return;
                }
                begin = syntax.tokens.get(0).path("characterOffsetBegin").asInt(-1);
                end = syntax.tokens.get(syntax.tokens.size() - 1).path("characterOffsetEnd").asInt(-1);
                requireSpan(begin, end);
                chunk = node(chunkId, "kex:Chunk");
                chunk.put("schema:text", text.substring(begin, end));
                chunk.put("schema:isPartOf", ref(document));
                chunk.put("kex:sentenceIndex", ordinal);
                chunk.put("kex:begin", begin);
                chunk.put("kex:end", end);
                nodes.add(chunk);
                for (int predicate : syntax.predicates) {
                    frames.put(predicate, node(eventId(predicate), "kex:SemanticFrame"));
                    add(chunk, "kex:events", ref(eventId(predicate)));
                }
                for (var entry : frames.entrySet()) {
                    populateFrame(entry.getKey(), entry.getValue());
                }
                for (var entry : frames.entrySet()) {
                    clauseStructure(entry.getKey(), entry.getValue());
                }
                nodes.addAll(frames.values());
            }

            private LexicalDisambiguation.Result sense(int index) {
                return senses.computeIfAbsent(index, key -> {
                    JsonNode token = syntax.byIndex.get(key);
                    return wsd.resolve(token.path("lemma").asText(token.path("word").asText()), token.path("pos").asText(),
                            OccurrenceContext.from(text, sentence, key));
                });
            }

            private void populateFrame(int predicate, Map<String, Object> frame) {
                JsonNode token = syntax.byIndex.get(predicate);
                String lemma = token.path("lemma").asText(token.path("word").asText());
                boolean copular = syntax.copular(predicate), passive = syntax.passive(predicate);
                frame.put("kex:predicate", copular ? "be" : lemma);
                frame.put("kex:predicateText", token.path("word").asText());
                frame.put("kex:predicateTokenIndex", predicate);
                frame.put("kex:begin", token.path("characterOffsetBegin").asInt());
                frame.put("kex:end", token.path("characterOffsetEnd").asInt());
                frame.put("prov:wasDerivedFrom", ref(chunkId));
                frame.put("kex:retrievalText", text.substring(begin, end));
                frame.put("kex:assertionStatus", "unasserted-event-description");
                frame.put("kex:dependencySource", "basic-enhanced-and-shared-coordination-subjects");
                frame.put("kex:voice", passive ? "passive" : syntax.participialModifier(predicate) ? "unspecified" : "active");
                if (copular) {
                    frame.put("kex:attributeText", token.path("word").asText());
                }
                var predicateSense = sense(predicate);
                frame.putAll(wsd.describe(predicateSense));
                var matches = copular ? List.<VerbNetLexicon.Match>of()
                        : VerbNetRoles.match(lemma, predicate, passive, syntax.arguments, syntax.byIndex);
                var syntacticMatches = matches;
                if (predicateSense.selected() != null && VerbNetRoles.index() != null && !copular) {
                    var aligned = matches.stream().filter(m -> VerbNetRoles.index().supportsSense(lemma,
                            m.classId(), predicateSense.senseKeys())).toList();
                    if (!aligned.isEmpty()) {
                        matches = aligned;
                    }
                    frame.put("kex:wordnetVerbnetAlignment", aligned.isEmpty() ? "no-mapped-candidate" : "sense-key-match");
                }
                for (var match : syntacticMatches) {
                    add(frame, "kex:verbnetSyntacticCandidate", match.classId() + "/" + match.frameId());
                }
                frame.put("kex:verbnetSyntacticCandidateCount", syntacticMatches.size());
                frame.put("kex:verbnetCandidateCount", matches.size());
                var roles = VerbNetRoles.consensus(matches);
                frame.put("kex:roleAssignmentMethod", roles.isEmpty() ? "dependency-rules-v2" : "verbnet-frame-consensus-v2");
                frame.put("kex:verbnetMatchStatus", VerbNetRoles.index() == null ? "disabled" : matches.isEmpty()
                        ? "no-compatible-frame" : matches.size() == 1 ? "single-syntactic-candidate" : "multiple-syntactic-candidates");
                for (var match : matches) {
                    add(frame, "kex:verbnetClass", match.classId());
                    add(frame, "kex:verbnetFrame", match.frameId());
                    add(frame, "kex:verbnetCandidate", match.classId() + "/" + match.frameId() + ": " + match.description());
                    add(frame, "kex:verbnetSyntax", match.syntax());
                    add(frame, "kex:verbnetMatchedArgumentCount", match.matchedArgumentCount());
                    for (int unmatched : match.unmatchedArguments()) {
                        add(frame, "kex:verbnetUnmatchedArgumentToken", unmatched);
                    }
                }
                if (!matches.isEmpty()) {
                    frame.put("kex:verbnetRestrictionsEvaluated", false);
                    frame.put("kex:verbnetSyntacticRestrictionsEvaluated", matches.stream().allMatch(VerbNetLexicon.Match::syntacticRestrictionsEvaluated));
                    frame.put("kex:verbnetSelectionalRestrictionsEvaluated", false);
                }
                boolean negative = false;
                for (JsonNode edge : syntax.outgoing(predicate)) {
                    int head = dependent(edge);
                    JsonNode argument = syntax.byIndex.get(head);
                    if (argument == null) {
                        continue;
                    }
                    String relation = relation(edge), word = argument.path("lemma").asText();
                    if (relation.equals("neg") || Set.of("not", "n't", "never").contains(word)) {
                        negative = true;
                    }
                    if (relation.startsWith("aux") && argument.path("pos").asText().equals("MD")) {
                        add(frame, "kex:modality", word);
                    }
                    String role = roles.getOrDefault(head, fallbackRole(edge, passive));
                    if (role == null) {
                        continue;
                    }
                    String method = roles.containsKey(head) ? "verbnet-frame-consensus-v2" : "dependency-rules-v2";
                    if (frames.containsKey(head)) {
                        add(frame, "kex:" + role, ref(eventId(head)));
                        mention(predicate, head, role, edge, method, ref(eventId(head)), true, null);
                    } else {
                        nominalArgument(predicate, head, role, edge, method);
                    }
                }
                frame.put("kex:negated", negative);
            }

            private void nominalArgument(int predicate, int head, String role, JsonNode edge, String method) {
                List<Integer> members = syntax.nominalConjuncts(head);
                Map<String, String> target;
                String group = null;
                if (members.size() > 1) {
                    group = chunkId + "_g" + head;
                    if (!entities.containsKey(group)) {
                        var node = node(group, "kex:ArgumentGroup");
                        for (int member : members) {
                            add(node, "kex:member", entity(member));
                            String connective = syntax.marker(member, "cc");
                            if (!connective.isBlank()) {
                                add(node, "kex:coordination", connective);
                            }
                        }
                        node.put("prov:wasDerivedFrom", ref(chunkId));
                        entities.put(group, node);
                    }
                    target = ref(group);
                } else {
                    target = entity(head);
                }
                add(frames.get(predicate), "kex:" + role, target);
                for (int member : members) {
                    mention(predicate, member, role, edge, method, entity(member), false, group);
                }
            }

            private Map<String, String> entity(int head) {
                List<JsonNode> words = words(syntax.phrase(head, false));
                int from = words.get(0).path("characterOffsetBegin").asInt();
                int to = words.get(words.size() - 1).path("characterOffsetEnd").asInt();
                requireSpan(from, to);
                String surface = text.substring(from, to);
                var coreference = corefs.getOrDefault(ordinal + ":" + words.get(0).path("index").asInt() + ":"
                        + (words.get(words.size() - 1).path("index").asInt() + 1), corefs.get(ordinal + ":head:" + head));
                String canonical = coreference == null ? surface : coreference.label();
                String identity = coreference == null ? ordinal + ":" + head : coreference.id();
                String id = document + "_e" + hash(identity);
                var entity = entities.computeIfAbsent(id, key -> {
                    var value = node(key, entityType(syntax.byIndex.get(head).path("ner").asText()));
                    value.put("schema:name", canonical);
                    value.put("kex:nerType", syntax.byIndex.get(head).path("ner").asText("O"));
                    value.put("kex:entityIdentityMethod", coreference == null ? "occurrence" : "corenlp-coreference-chain");
                    return value;
                });
                add(entity, "kex:mentionedIn", ref(chunkId));
                add(chunk, "schema:mentions", ref(id));
                return ref(id);
            }

            private void mention(int predicate, int head, String role, JsonNode edge, String method,
                    Map<String, String> target, boolean clause, String group) {
                List<JsonNode> words = words(syntax.phrase(head, clause));
                int from = words.get(0).path("characterOffsetBegin").asInt();
                int to = words.get(words.size() - 1).path("characterOffsetEnd").asInt();
                requireSpan(from, to);
                String id = eventId(predicate) + "_" + role + "_" + head;
                if (nodes.stream().anyMatch(n -> id.equals(n.get("@id")))) {
                    return;
                }
                var mention = node(id, "kex:RoleMention");
                mention.put("kex:role", role);
                mention.put("kex:roleAssignmentMethod", method);
                mention.put("kex:dependencyRelation", relation(edge));
                mention.put("kex:syntacticEvidence", head == dependent(edge) ? syntax.source(edge) : "nominal-conjunction");
                mention.put("kex:headTokenIndex", head);
                mention.put(clause ? "kex:eventArgument" : "kex:entity", target);
                if (group != null) {
                    mention.put("kex:argumentGroup", ref(group));
                }
                mention.put("schema:text", text.substring(from, to));
                mention.put("kex:begin", from);
                mention.put("kex:end", to);
                mention.put("prov:wasDerivedFrom", ref(chunkId));
                mention.putAll(wsd.describe(sense(head)));
                nodes.add(mention);
                add(frames.get(predicate), "kex:roleMention", ref(id));
            }

            private void clauseStructure(int predicate, Map<String, Object> frame) {
                JsonNode incoming = syntax.incoming(predicate);
                String relation = incoming == null ? "ROOT" : relation(incoming);
                String type = syntax.participialModifier(predicate) ? "participial-modifier"
                        : relation.startsWith("acl:relcl") || relation.equals("rcmod") ? "relative" : baseRelation(relation);
                frame.put("kex:clauseType", type.equalsIgnoreCase("root") ? "root" : type);
                String marker = syntax.marker(predicate, type.equals("conj") ? "cc" : "mark");
                if (!marker.isBlank()) {
                    frame.put("kex:connective", marker);
                }
                if (incoming == null) {
                    return;
                }
                int parent = governor(incoming);
                if (frames.containsKey(parent)) {
                    String link = switch (type) {
                        case "ccomp" ->
                            "clausalComplement";
                        case "xcomp" ->
                            "openComplement";
                        case "csubj" ->
                            "clausalSubject";
                        case "advcl" ->
                            "adverbialClause";
                        case "conj" ->
                            "coordinatedWith";
                        default ->
                            "dependentFrame";
                    };
                    add(frames.get(parent), "kex:" + link, ref(eventId(predicate)));
                    if (!type.equals("conj")) {
                        frame.put("kex:embeddedInFrame", ref(eventId(parent))); 
                    }else {
                        frame.put("kex:coordinationScope", ref(eventId(parent)));
                    }
                    if (type.equals("advcl") && Set.of("if", "unless", "provided", "provided that").contains(marker)) {
                        add(frames.get(parent), "kex:condition", ref(eventId(predicate)));
                        frames.get(parent).put("kex:conditional", true);
                        frame.put("kex:conditionConnective", marker);
                    }
                } else if (Set.of("relative", "acl", "ccomp", "participial-modifier").contains(type) && syntax.byIndex.containsKey(parent)) {
                    var antecedent = entity(parent);
                    String property = type.equals("relative") ? "relativeAntecedent"
                            : type.equals("participial-modifier") ? "modifiedEntity" : "nominalComplementOf";
                    frame.put("kex:" + property, antecedent);
                    add(entities.get(antecedent.get("@id")), type.equals("relative") || type.equals("participial-modifier")
                            ? "kex:qualifiedByFrame" : "kex:clausalContent", ref(eventId(predicate)));
                }
            }

            private String fallbackRole(JsonNode edge, boolean passive) {
                String relation = relation(edge);
                JsonNode token = syntax.byIndex.get(dependent(edge));
                if (relation.startsWith("nsubj")) {
                    return passive ? "patient" : "agent";
                }
                if (Set.of("obj", "dobj").contains(relation)) {
                    return "theme";
                }
                if (relation.equals("iobj")) {
                    return "recipient";
                }
                if (relation.startsWith("csubj")) {
                    return "clausalSubject";
                }
                if (relation.equals("ccomp")) {
                    return "proposition";
                }
                if (relation.equals("xcomp")) {
                    return frames.containsKey(dependent(edge)) ? "action" : "result";
                }
                if (relation.startsWith("obl") || relation.startsWith("nmod")) {
                    String prep = syntax.marker(dependent(edge), "case"), ner = token.path("ner").asText();
                    if (relation.endsWith(":agent") || passive && prep.equals("by")) {
                        return "agent";
                    }
                    if (relation.endsWith(":tmod") || Set.of("DATE", "TIME", "DURATION").contains(ner)) {
                        return "time";
                    }
                    if (Set.of("CITY", "COUNTRY", "LOCATION", "STATE_OR_PROVINCE").contains(ner)) {
                        return "location";
                    }
                    if (Set.of("using", "via").contains(prep)) {
                        return "instrument";
                    }
                    return "oblique";
                }
                return null;
            }

            private List<JsonNode> words(Set<Integer> indices) {
                return syntax.tokens.stream().filter(t -> indices.contains(t.path("index").asInt())).toList();
            }

            private String eventId(int predicate) {
                return chunkId + "_p" + predicate;
            }
        }

        private void requireSpan(int begin, int end) {
            if (begin < 0 || end <= begin || end > text.length()) {
                throw new IllegalArgumentException("Invalid original-text evidence span");
            }
        }
    }

    private record Coreference(String id, String label) {}

    private static Map<String, Coreference> coreferences(JsonNode annotation) {
        Map<String, Coreference> result = new HashMap<>();
        for (JsonNode chain : annotation.path("corefs")) {
            JsonNode representative = null;
            for (JsonNode mention : chain) {
                if (mention.path("isRepresentativeMention").asBoolean()) {
                    representative = mention;
                }
            }
            if (representative == null || representative.path("text").asText().isBlank()) {
                continue;
            }
            var coreference = new Coreference("coref:" + representative.path("sentNum").asInt() + ":"
                    + representative.path("startIndex").asInt() + ":" + representative.path("endIndex").asInt(),
                    representative.path("text").asText());
            for (JsonNode mention : chain) {
                result.put(mention.path("sentNum").asInt() + ":" + mention.path("startIndex").asInt() + ":" + mention.path("endIndex").asInt(), coreference);
                if (mention.path("headIndex").isIntegralNumber()) {
                    result.put(mention.path("sentNum").asInt() + ":head:" + mention.path("headIndex").asInt(), coreference);
                }
            }
        }
        return result;
    }

    private static String entityType(String ner) {
        return switch (ner) {
            case "PERSON" ->
                "schema:Person";
            case "ORGANIZATION" ->
                "schema:Organization";
            case "CITY", "COUNTRY", "LOCATION", "STATE_OR_PROVINCE" ->
                "schema:Place";
            default ->
                "schema:Thing";
        };
    }

    private static Map<String, Object> node(String id, String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@id", id);
        node.put("@type", type);
        return node;
    }

    private static Map<String, String> ref(String id) {
        return Map.of("@id", id);
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> node, String key, Object value) {
        List<Object> values = (List<Object>) node.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

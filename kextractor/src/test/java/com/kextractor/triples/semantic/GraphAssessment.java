package com.kextractor.triples.semantic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.nlp.disambiguation.LexicalDisambiguation;
import com.kextractor.triples.GraphSerializer;
import com.kextractor.utils.GraphFormat;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.JSONOutputter;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

public final class GraphAssessment {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEX = "http://kextractor.com/entity/";
    private static final Path DEFAULT_DATASET = Path.of("kextractor/src/test/resources/graph/complex-texts.json");

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: GraphAssessment [DATASET [OUTPUT_DIRECTORY]]");
        }
        Path datasetPath = resolve(args.length > 0 ? Path.of(args[0]) : DEFAULT_DATASET);
        Path output = args.length > 1 ? Path.of(args[1]).toAbsolutePath().normalize()
                : repositoryRoot(datasetPath).resolve("reports/graph-assessment");
        JsonNode dataset = MAPPER.readTree(datasetPath.toFile());
        Set<String> ids = new HashSet<>();
        if (!dataset.path("cases").isArray() || dataset.path("cases").isEmpty()) {
            throw new IllegalArgumentException("Dataset has no cases: " + datasetPath);
        }
        for (JsonNode item : dataset.path("cases")) {
            String id = item.path("id").asText();
            if (!id.matches("[a-z0-9-]+") || !ids.add(id) || item.path("text").asText().isBlank()) {
                throw new IllegalArgumentException("Invalid case: " + id);
            }
        }
        Files.createDirectories(output.resolve("annotations"));
        Properties properties = new Properties();
        properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
        properties.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");
        properties.setProperty("ner.applyFineGrained", "false");
        properties.setProperty("ner.useSUTime", "false");
        properties.setProperty("depparse.extradependencies", "MAXIMAL");
        properties.setProperty("threads", "1");
        properties.setProperty("depparse.nthreads", "1");
        Instant started = Instant.now();
        StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> backend = new LinkedHashMap<>();
        int failed = 0;
        try (LexicalDisambiguation wsd = LexicalDisambiguation.configured()) {
            backend.put("modelDirectory", wsd.modelDirectory().toString());
            backend.put("method", LexicalDisambiguation.METHOD);
            backend.put("wordnetVersion", wsd.wordnetVersion());
            backend.put("threshold", wsd.threshold());
            backend.put("margin", wsd.margin());
            for (JsonNode item : dataset.path("cases")) {
                String id = item.path("id").asText(), text = item.path("text").asText();
                System.out.println("Building graph: " + id);
                long before = System.nanoTime();
                Annotation annotation = new Annotation(text);
                pipeline.annotate(annotation);
                JsonNode parsed = MAPPER.readTree(JSONOutputter.jsonPrint(annotation));
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.resolve("annotations/" + id + ".json").toFile(), parsed);
                Map<String, Object> graph = SemanticGraphBuilder.build(text, parsed, wsd);
                JsonNode json = MAPPER.valueToTree(graph);
                String owl = GraphSerializer.serialize(graph, GraphFormat.OWL);
                Files.writeString(output.resolve(id + ".txt"), text + "\n", StandardCharsets.UTF_8);
                Files.writeString(output.resolve(id + ".owl"), owl, StandardCharsets.UTF_8);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.resolve(id + ".json").toFile(), graph);
                List<Map<String, Object>> checks = assess(text, json, owl, item.path("expectations"));
                long failures = checks.stream().filter(c -> !Boolean.TRUE.equals(c.get("passed"))).count();
                failed += failures;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("textSha256", sha256(text));
                row.put("owlFile", id + ".owl");
                row.put("jsonFile", id + ".json");
                row.put("annotationFile", "annotations/" + id + ".json");
                row.put("sentences", parsed.path("sentences").size());
                row.put("nodes", json.path("@graph").size());
                row.put("frames", nodesOfType(json, "kex:SemanticFrame").size());
                row.put("verbnetFrames", nodesOfType(json, "kex:SemanticFrame").stream().filter(f -> f.has("kex:verbnetFrame")).count());
                row.put("checks", checks);
                row.put("failedChecks", failures);
                row.put("milliseconds", (System.nanoTime() - before) / 1_000_000.0);
                results.add(row);
                System.out.printf("Exported %s.owl: %s frames, %d/%d checks passed.%n", id,
                        row.get("frames"), checks.size() - failures, checks.size());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("assessment", dataset.path("name").asText());
        report.put("limitations", dataset.path("description").asText());
        report.put("dataset", datasetPath.toString());
        report.put("datasetSha256", sha256(Files.readString(datasetPath)));
        report.put("started", started.toString());
        report.put("completed", Instant.now().toString());
        report.put("parserProperties", properties);
        report.put("wsd", backend);
        report.put("cases", results);
        report.put("failedChecks", failed);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), report);
        System.out.println("Graph assessment report: " + output.resolve("summary.json"));
        if (failed > 0) {
            throw new IllegalStateException(failed + " graph assessment checks failed; see summary.json");
        }
    }

    static List<Map<String, Object>> assess(String text, JsonNode graph, String owl, JsonNode expected) {
        List<Map<String, Object>> checks = new ArrayList<>();
        List<JsonNode> frames = nodesOfType(graph, "kex:SemanticFrame");
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        for (JsonNode node : graph.path("@graph")) {
            nodes.put(node.path("@id").asText(), node);
        }
        List<String> dangling = new ArrayList<>();
        for (JsonNode node : graph.path("@graph")) {
            findReferences(node, nodes.keySet(), dangling);
        }
        check(checks, "internal-references", dangling.isEmpty(), dangling.toString());
        List<String> spans = new ArrayList<>();
        for (JsonNode node : graph.path("@graph")) {
            if (!node.has("kex:begin") && !node.has("kex:end")) {
                continue;
            }
            int begin = node.path("kex:begin").asInt(-1), end = node.path("kex:end").asInt(-1);
            boolean valid = begin >= 0 && end > begin && end <= text.length();
            if (valid && node.has("schema:text")) {
                valid = text.substring(begin, end).equals(node.path("schema:text").asText());
            }
            if (valid && node.has("kex:predicateText")) {
                valid = text.substring(begin, end).equals(node.path("kex:predicateText").asText());
            }
            if (!valid) {
                spans.add(node.path("@id").asText());
            }
        }
        check(checks, "original-source-spans", spans.isEmpty(), spans.toString());
        check(checks, "event-description-scope", !frames.isEmpty() && frames.stream().allMatch(f
                -> f.path("kex:assertionStatus").asText().equals("unasserted-event-description")),
                "All semantic frames must preserve event-description scope.");
        var model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(owl.getBytes(StandardCharsets.UTF_8)), Lang.RDFXML);
            check(checks, "owl-ontology-roundtrip", model.contains(null, RDF.type, OWL.Ontology),
                    model.size() + " RDF statements reparsed from OWL RDF/XML.");
            check(checks, "owl-frame-class-declaration", model.contains(model.createResource(KEX + "SemanticFrame"), RDF.type, OWL.Class),
                    "kex:SemanticFrame is declared as an OWL class.");
            check(checks, "owl-predicate-data-property", model.contains(model.createResource(KEX + "predicate"), RDF.type, OWL.DatatypeProperty),
                    "Predicate lemma is a data property, distinct from frame role object properties.");
            check(checks, "owl-role-mention-object-property", model.contains(model.createResource(KEX + "roleMention"), RDF.type, OWL.ObjectProperty),
                    "Role mentions link to occurrence resources.");
            boolean disjoint = !model.listResourcesWithProperty(RDF.type, OWL.ObjectProperty).toList().stream()
                    .anyMatch(r -> model.contains(r, RDF.type, OWL.DatatypeProperty));
            check(checks, "owl-property-kinds-disjoint", disjoint, "Object and data properties use separate IRIs.");
        } finally {
            model.close();
        }
        for (JsonNode predicate : expected.path("predicates")) {
            check(checks, "predicate:" + predicate.asText(), frames.stream().anyMatch(f -> predicate.asText().equals(f.path("kex:predicate").asText())),
                    "Expected predicate lemma: " + predicate.asText());
        }
        for (JsonNode property : expected.path("frameProperties")) {
            check(checks, "frame-property:" + property.path("predicate").asText() + ":" + property.path("property").asText(),
                    frames.stream().filter(f -> f.path("kex:predicate").asText().equals(property.path("predicate").asText()))
                            .anyMatch(f -> contains(f.path(property.path("property").asText()), property.path("value"))), property.toString());
        }
        for (JsonNode argument : expected.path("roleArguments")) {
            boolean found = frames.stream().filter(f -> f.path("kex:predicate").asText().equals(argument.path("predicate").asText()))
                    .flatMap(f -> {
                        List<JsonNode> mentions = new ArrayList<>();
                        for (JsonNode reference : f.path("kex:roleMention")) {
                            JsonNode mention = nodes.get(reference.path("@id").asText());
                            if (mention != null) {
                                mentions.add(mention);
                            }
                        }
                        return mentions.stream();
                    }).anyMatch(m -> m.path("kex:wsdTargetLemma").asText().equalsIgnoreCase(argument.path("lemma").asText())
                    && contains(argument.path("roles"), m.path("kex:role"))
                    && (!argument.has("method") || argument.path("method").equals(m.path("kex:roleAssignmentMethod"))));
            check(checks, "role-argument:" + argument.path("predicate").asText() + ":" + argument.path("lemma").asText(),
                    found, argument.toString());
        }
        for (JsonNode link : expected.path("links")) {
            Set<String> targets = new HashSet<>();
            frames.stream().filter(f -> f.path("kex:predicate").asText().equals(link.path("toPredicate").asText()))
                    .forEach(f -> targets.add(f.path("@id").asText()));
            boolean found = frames.stream().filter(f -> f.path("kex:predicate").asText().equals(link.path("fromPredicate").asText()))
                    .anyMatch(f -> referencesAny(f.path(link.path("property").asText()), targets));
            check(checks, "frame-link:" + link.path("fromPredicate").asText() + ":" + link.path("property").asText(), found, link.toString());
        }
        for (JsonNode predicate : expected.path("relativePredicates")) {
            check(checks, "relative-antecedent:" + predicate.asText(), frames.stream()
                    .filter(f -> f.path("kex:predicate").asText().equals(predicate.asText()))
                    .anyMatch(f -> f.path("kex:relativeAntecedent").has("@id")), "Relative predicate must reference its nominal antecedent.");
        }
        for (JsonNode predicate : expected.path("modifiedPredicates")) {
            check(checks, "participial-modifier:" + predicate.asText(), frames.stream()
                    .filter(f -> f.path("kex:predicate").asText().equals(predicate.asText()))
                    .anyMatch(f -> f.path("kex:clauseType").asText().equals("participial-modifier")
                            && f.path("kex:modifiedEntity").has("@id")),
                    "Participial predicates must qualify their nominal head without guessing an active event.");
        }
        for (JsonNode group : expected.path("argumentGroups")) {
            boolean found = nodesOfType(graph, "kex:ArgumentGroup").stream()
                    .anyMatch(n -> contains(n.path("kex:coordination"), group.path("coordination"))
                            && n.path("kex:member").size() >= group.path("minimumMembers").asInt(2));
            check(checks, "argument-group:" + group.path("coordination").asText(), found, group.toString());
        }
        if (expected.has("minimumVerbNetFrames")) {
            long count = frames.stream().filter(f -> f.has("kex:verbnetFrame")).count();
            check(checks, "verbnet-compatible-frames", count >= expected.path("minimumVerbNetFrames").asInt(),
                    count + " frames have compatible VerbNet syntax candidates; candidate compatibility does not establish a unique lexical sense.");
        }
        return checks;
    }

    private static List<JsonNode> nodesOfType(JsonNode graph, String type) {
        List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode node : graph.path("@graph")) {
            if (node.path("@type").asText().equals(type)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static boolean contains(JsonNode node, JsonNode value) {
        if (node.equals(value)) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (contains(child, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean referencesAny(JsonNode node, Set<String> targets) {
        if (node.isObject()) {
            return targets.contains(node.path("@id").asText());
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (referencesAny(child, targets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void findReferences(JsonNode node, Set<String> known, List<String> missing) {
        if (node.isObject() && node.has("@id")) {
            String id = node.path("@id").asText();
            if (id.startsWith("kex:") && !known.contains(id)) {
                missing.add(id);
            }
        }
        for (JsonNode child : node) {
            if (child.isContainerNode()) {
                findReferences(child, known, missing);
            }
        }
    }

    private static void check(List<Map<String, Object>> checks, String name, boolean passed, String detail) {
        checks.add(Map.of("name", name, "passed", passed, "detail", detail));
    }

    private static Path resolve(Path path) throws Exception {
        if (path.isAbsolute() && Files.isRegularFile(path)) {
            return path.normalize();
        }
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(path).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new java.io.FileNotFoundException("Dataset not found: " + path);
    }

    private static Path repositoryRoot(Path dataset) {
        for (Path directory = dataset.toAbsolutePath().getParent(); directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("kextractor/src"))) {
                return directory;
            }
        }
        return Path.of("").toAbsolutePath();
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}

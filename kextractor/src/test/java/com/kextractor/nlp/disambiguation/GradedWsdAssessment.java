package com.kextractor.nlp.disambiguation;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class GradedWsdAssessment {

    private static final Path DEFAULT_DATASET = Path.of(
            "kextractor/src/test/resources/wsd/graded-wsd-assessment.json");
    private static final Path DEFAULT_REPORT = Path.of("reports/graded-wsd-assessment.json");

    private record Target(String id, String category, String text, String surface, String lemma,
            String pos, int begin, int end, Set<String> goldSenseKeys) {

    }

    public static void main(String[] args) throws Exception {
        if (args.length > 3 || (args.length == 3 && !"validate".equals(args[2]))) {
            throw new IllegalArgumentException(
                    "Usage: GradedWsdAssessment [DATASET [REPORT [validate]]]");
        }
        Path datasetPath = resolveDataset(args.length > 0 ? Path.of(args[0]) : DEFAULT_DATASET);
        Path reportPath = (args.length > 1 ? Path.of(args[1]) : DEFAULT_REPORT).toAbsolutePath().normalize();
        boolean validateOnly = args.length == 3;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dataset = mapper.readTree(datasetPath.toFile());
        List<Target> targets;
        String dictionaryVersion;
        try (WordNetRepository dictionary = WordNetRepository.configured()) {
            dictionaryVersion = dictionary.version();
            targets = validate(dataset, dictionary);
        }
        System.out.printf("Validated %d sentences and %d target occurrences (WordNet %s).%n",
                dataset.path("cases").size(), targets.size(), dictionaryVersion);
        if (validateOnly) {
            System.out.println("Validation only: model inference was not started.");
            return;
        }

        Instant started = Instant.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        Metrics overall = new Metrics();
        Map<String, Metrics> byCategory = new TreeMap<>();
        Map<String, Metrics> byPos = new TreeMap<>();
        Map<String, Object> backendSettings = new LinkedHashMap<>();
        try (LexicalDisambiguation wsd = LexicalDisambiguation.configured()) {
            for (Target target : targets) {
                long before = System.nanoTime();
                var result = wsd.resolve(target.lemma(), target.pos(),
                        OccurrenceContext.of(target.text(), target.begin(), target.end()));
                double milliseconds = (System.nanoTime() - before) / 1_000_000.0;
                Map<String, Object> evidence = wsd.describe(result);
                String method = String.valueOf(evidence.get("kex:wsdMethod"));
                if (!LexicalDisambiguation.METHOD.equals(method)) {
                    throw new IllegalStateException("Expected graded-WSD backend, received: " + method);
                }
                if (evidence.containsKey("kex:wsdFallbackReason")) {
                    throw new IllegalStateException("Refusing an assessment report containing fallback results");
                }
                if (backendSettings.isEmpty()) {
                    for (String key : List.of("kex:wsdMethod", "kex:wsdModel", "kex:wsdModelDirectory", "kex:wsdThreshold",
                            "kex:wsdMargin", "kex:wordnetVersion", "kex:wsdScoreType")) {
                        if (evidence.containsKey(key)) {
                            backendSettings.put(key, evidence.get(key));
                        }
                    }
                }

                List<Map<String, Object>> ranked = new ArrayList<>();
                Set<String> uniqueSynsets = new TreeSet<>();
                int goldRank = 0;
                double precedingScore = Double.POSITIVE_INFINITY;
                for (int index = 0; index < result.candidates().size(); index++) {
                    var candidate = result.candidates().get(index);
                    String synsetId = candidate.synset().getID().toString();
                    if (!Double.isFinite(candidate.score()) || candidate.score() > precedingScore
                            || !uniqueSynsets.add(synsetId)) {
                        throw new IllegalStateException("Invalid ranking for " + target.id());
                    }
                    precedingScore = candidate.score();
                    boolean correct = candidate.senseKeys().stream().anyMatch(target.goldSenseKeys()::contains);
                    if (correct && goldRank == 0) {
                        goldRank = index + 1;
                    }
                    Map<String, Object> candidateRow = new LinkedHashMap<>();
                    candidateRow.put("rank", index + 1);
                    candidateRow.put("synsetId", synsetId);
                    candidateRow.put("senseKeys", new TreeSet<>(candidate.senseKeys()));
                    candidateRow.put("rawScore", candidate.score());
                    candidateRow.put("gloss", candidate.synset().getGloss());
                    if (!target.goldSenseKeys().isEmpty()) {
                        candidateRow.put("matchesGold", correct);
                    }
                    ranked.add(candidateRow);
                }
                if (!target.goldSenseKeys().isEmpty() && goldRank == 0) {
                    throw new IllegalStateException("Gold sense disappeared from backend candidates: " + target.id());
                }
                boolean accepted = result.selected() != null;
                boolean acceptedCorrect = accepted && result.selected().senseKeys().stream()
                        .anyMatch(target.goldSenseKeys()::contains);
                if (accepted && !uniqueSynsets.contains(result.selected().synset().getID().toString())) {
                    throw new IllegalStateException("Selected sense is absent from ranking: " + target.id());
                }
                boolean topCorrect = goldRank == 1;
                overall.add(target, topCorrect, accepted, acceptedCorrect, ranked.isEmpty());
                byCategory.computeIfAbsent(target.category(), ignored -> new Metrics())
                        .add(target, topCorrect, accepted, acceptedCorrect, ranked.isEmpty());
                byPos.computeIfAbsent(posGroup(target.pos()), ignored -> new Metrics())
                        .add(target, topCorrect, accepted, acceptedCorrect, ranked.isEmpty());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", target.id());
                row.put("category", target.category());
                row.put("text", target.text());
                row.put("surface", target.surface());
                row.put("lemma", target.lemma());
                row.put("pos", target.pos());
                row.put("start", target.begin());
                row.put("end", target.end());
                row.put("goldSenseKeys", new TreeSet<>(target.goldSenseKeys()));
                row.put("underspecified", target.goldSenseKeys().isEmpty());
                row.put("accepted", accepted);
                if (!target.goldSenseKeys().isEmpty()) {
                    row.put("goldRank", goldRank);
                    row.put("topCorrect", topCorrect);
                    row.put("acceptedCorrect", acceptedCorrect);
                }
                row.put("inferenceMilliseconds", milliseconds);
                row.put("ranking", ranked);
                row.put("evidence", evidence);
                rows.add(row);
                System.out.printf("Evaluated %s [%s:%d-%d]: %s%n", target.id(), target.surface(),
                        target.begin(), target.end(), accepted ? "selected" : "ambiguous");
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("assessment", "ChangeIsKey/graded-wsd");
        report.put("dataset", datasetPath.toString());
        report.put("datasetSha256", sha256(datasetPath));
        report.put("limitations", dataset.path("description").asText());
        report.put("split", dataset.path("split").asText("unspecified"));
        report.put("offsetUnit", "UTF-16 code units; start inclusive, end exclusive");
        report.put("startedAt", started.toString());
        report.put("completedAt", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("wordnetVersion", dictionaryVersion);
        report.put("backend", backendSettings);
        report.put("settings", configuredSettings());
        report.put("modelResources", modelResources(backendSettings));
        report.put("summary", overall.describe());
        report.put("byCategory", describeMetrics(byCategory));
        report.put("byPos", describeMetrics(byPos));
        report.put("rows", rows);
        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println(overall.describe());
        System.out.println("Report: " + reportPath);
    }

    private static List<Target> validate(JsonNode dataset, WordNetRepository dictionary) {
        if (!dataset.path("cases").isArray() || dataset.path("cases").isEmpty()) {
            throw new IllegalArgumentException("Dataset must contain a nonempty cases array");
        }
        String declaredVersion = dataset.path("wordnetVersion").asText();
        if (!declaredVersion.isBlank() && !declaredVersion.equals(dictionary.version())) {
            throw new IllegalArgumentException("Dataset WordNet " + declaredVersion
                    + " does not match dictionary " + dictionary.version());
        }
        List<Target> targets = new ArrayList<>();
        Set<String> ids = new TreeSet<>();
        for (JsonNode item : dataset.path("cases")) {
            String id = requiredText(item, "id");
            String text = requiredText(item, "text");
            String category = requiredText(item, "category");
            if (!ids.add(id) || !item.path("targets").isArray() || item.path("targets").isEmpty()) {
                throw new IllegalArgumentException("Duplicate case ID or missing targets: " + id);
            }
            Set<String> spans = new TreeSet<>();
            for (JsonNode target : item.path("targets")) {
                String surface = requiredText(target, "surface");
                String lemma = requiredText(target, "lemma");
                String pos = requiredText(target, "pos");
                posGroup(pos);
                if (!target.path("start").isIntegralNumber() || !target.path("end").isIntegralNumber()
                        || !target.path("start").canConvertToInt() || !target.path("end").canConvertToInt()) {
                    throw new IllegalArgumentException("Offsets must be integers in " + id);
                }
                int begin = target.path("start").asInt(-1), end = target.path("end").asInt(-1);
                if (begin < 0 || end <= begin || end > text.length()
                        || !text.substring(begin, end).equals(surface)
                        || splitsSurrogate(text, begin) || splitsSurrogate(text, end)
                        || !spans.add(begin + ":" + end)) {
                    throw new IllegalArgumentException("Invalid or duplicate UTF-16 target span in " + id);
                }
                if (!target.path("goldSenseKeys").isArray()) {
                    throw new IllegalArgumentException("goldSenseKeys must be an array in " + id);
                }
                Set<String> gold = new TreeSet<>();
                target.path("goldSenseKeys").forEach(key -> {
                    if (!key.isTextual() || key.asText().isBlank() || !gold.add(key.asText())) {
                        throw new IllegalArgumentException("Invalid or duplicate gold key in " + id);
                    }
                });
                String expected = requiredText(target, "expected");
                if (!(gold.isEmpty() ? "abstain" : "select").equals(expected)) {
                    throw new IllegalArgumentException("Inconsistent gold keys/expectation in " + id);
                }
                Set<String> known = new TreeSet<>();
                for (var sense : dictionary.candidates(lemma, pos)) {
                    known.addAll(sense.senseKeys());
                }
                if (known.isEmpty() || !known.containsAll(gold)) {
                    throw new IllegalArgumentException("Unknown WordNet lemma/POS or gold sense in " + id
                            + ": " + lemma + "/" + pos + " " + gold);
                }
                targets.add(new Target(id, category, text, surface, lemma, pos, begin, end, Set.copyOf(gold)));
            }
        }
        return List.copyOf(targets);
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing nonempty string field: " + key);
        }
        return value.asText();
    }

    private static boolean splitsSurrogate(String text, int position) {
        return position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1))
                && Character.isLowSurrogate(text.charAt(position));
    }

    private static String posGroup(String pos) {
        if (pos.startsWith("NN")) {
            return "noun";
        }
        if (pos.startsWith("VB")) {
            return "verb";
        }
        if (pos.startsWith("JJ")) {
            return "adjective";
        }
        if (pos.startsWith("RB")) {
            return "adverb";
        }
        throw new IllegalArgumentException("Unsupported Penn POS: " + pos);
    }

    private static Path resolveDataset(Path requested) throws FileNotFoundException {
        if (Files.isRegularFile(requested)) {
            return requested.toAbsolutePath().normalize();
        }
        if (!requested.isAbsolute()) {
            for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
                for (Path candidate : List.of(base.resolve(requested), base.resolve("kextractor").resolve(requested))) {
                    if (Files.isRegularFile(candidate)) {
                        return candidate.normalize();
                    }
                }
            }
        }
        throw new FileNotFoundException("Assessment dataset missing: " + requested.toAbsolutePath().normalize());
    }

    private static Map<String, String> configuredSettings() {
        Map<String, String> settings = new TreeMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("kextractor.wsd.") || name.startsWith("kextractor.wordnet.")
                    || name.startsWith("kextractor.omw.")) {
                settings.put(name, System.getProperty(name));
            }
        }
        return settings;
    }

    private static Map<String, Object> modelResources(Map<String, Object> backend) throws Exception {
        Path modelDir = Path.of(backend.get("kex:wsdModelDirectory").toString());
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("directory", modelDir.toString());
        Map<String, String> hashes = new TreeMap<>();
        for (String filename : List.of("model.onnx", "model.onnx_data", "model.onnx.data",
                "tokenizer.json", "tokenizer_config.json", "added_tokens.json", "special_tokens_map.json",
                "vocab.json", "merges.txt", "config.json")) {
            Path path = modelDir.resolve(filename);
            if (Files.isRegularFile(path)) {
                hashes.put(filename, sha256(path));
            }
        }
        resources.put("sha256", hashes);
        return resources;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Map<String, Object> describeMetrics(Map<String, Metrics> metrics) {
        Map<String, Object> result = new LinkedHashMap<>();
        metrics.forEach((key, value) -> result.put(key, value.describe()));
        return result;
    }

    private static final class Metrics {

        private int labelled, topCorrect, accepted, acceptedCorrect, noCandidates;
        private int underspecified, underspecifiedAccepted;

        private void add(Target target, boolean top, boolean selected, boolean correct, boolean empty) {
            if (target.goldSenseKeys().isEmpty()) {
                underspecified++;
                if (selected) {
                    underspecifiedAccepted++;
                }
                return;
            }
            labelled++;
            if (top) {
                topCorrect++;
            }
            if (selected) {
                accepted++;
            }
            if (correct) {
                acceptedCorrect++;
            }
            if (empty) {
                noCandidates++;
            }
        }

        private Map<String, Object> describe() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("labelledTargets", labelled);
            values.put("topCorrect", topCorrect);
            values.put("topAccuracy", labelled == 0 ? null : (double) topCorrect / labelled);
            values.put("accepted", accepted);
            values.put("acceptedCorrect", acceptedCorrect);
            values.put("acceptedWrong", accepted - acceptedCorrect);
            values.put("coverage", labelled == 0 ? null : (double) accepted / labelled);
            values.put("acceptedAccuracy", accepted == 0 ? null : (double) acceptedCorrect / accepted);
            values.put("noCandidates", noCandidates);
            values.put("underspecifiedTargets", underspecified);
            values.put("underspecifiedAccepted", underspecifiedAccepted);
            return values;
        }
    }

    private GradedWsdAssessment() {
    }
}

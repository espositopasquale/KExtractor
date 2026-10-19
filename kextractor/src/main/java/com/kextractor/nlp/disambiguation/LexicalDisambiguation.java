package com.kextractor.nlp.disambiguation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LexicalDisambiguation implements AutoCloseable {

    public static final String METHOD = "graded-wsd-onnx-v1";

    public record Candidate(edu.mit.jwi.item.ISynset synset, double score, Set<String> senseKeys) {

        public Candidate {
            senseKeys = Set.copyOf(senseKeys);
        }
    }

    public record Result(List<Candidate> candidates, Candidate selected,
            String lemma, String pos, OccurrenceContext context) {

        public Result {
            candidates = List.copyOf(candidates);
        }

        public Set<String> senseKeys() {
            return selected == null ? Set.of() : selected.senseKeys();
        }

        public String method() {
            return METHOD;
        }
    }
    private final WordNetRepository wordnet;
    private final GradedWsdModel model;
    private final OmwMultilingualRepository omw;
    private final double threshold;
    private final double margin;
    private final String language;

    public static LexicalDisambiguation configured() {
        try {
            return new LexicalDisambiguation();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize graded-wsd: " + e.getMessage(), e);
        }
    }

    private LexicalDisambiguation() throws Exception {
        if (System.getProperty("kextractor.wsd.strategy") != null) {
            throw new IllegalArgumentException("Legacy wsd.strategy has been removed; graded-wsd is the only backend");
        }
        threshold = Double.parseDouble(System.getProperty("kextractor.wsd.threshold", "2.5"));
        margin = Double.parseDouble(System.getProperty("kextractor.wsd.margin", "0.25"));
        if (!Double.isFinite(threshold) || !Double.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException("WSD threshold must be finite and margin nonnegative");
        }
        language = System.getProperty("kextractor.omw.language", "ita").toLowerCase(Locale.ROOT);
        WordNetRepository dictionary = WordNetRepository.configured();
        GradedWsdModel loaded = null;
        OmwMultilingualRepository translations = null;
        try {
            String db = System.getProperty("kextractor.omw.db", System.getenv("KEX_OMW_DB"));
            if (db == null) {
                for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
                    Path candidate = base.resolve("omw_multilingual.db");
                    if (Files.isRegularFile(candidate)) {
                        db = candidate.toString();
                        break;
                    }
                }
            }
            if (db != null && !db.equalsIgnoreCase("off")) {
                if (!dictionary.version().equals("3.0")) {
                    throw new IllegalArgumentException("OMW offset lookup requires compatible Princeton WordNet 3.0");
                }
                translations = new OmwMultilingualRepository(db);
            }
            loaded = new GradedWsdModel(GradedWsdModel.configuredDirectory());
        } catch (Exception | LinkageError e) {
            dictionary.close();
            if (loaded != null) try {
                loaded.close();
            } catch (Exception close) {
                e.addSuppressed(close);
            }
            if (translations != null) try {
                translations.close();
            } catch (Exception close) {
                e.addSuppressed(close);
            }
            throw e;
        }
        wordnet = dictionary;
        model = loaded;
        omw = translations;
    }

    public boolean enabled() {
        return true;
    }

    public String wordnetVersion() {
        return wordnet.version();
    }

    public Path modelDirectory() {
        return model.directory();
    }

    public double threshold() {
        return threshold;
    }

    public double margin() {
        return margin;
    }

    public Result resolve(String lemma, String pennPos, OccurrenceContext context) {
        List<WordNetRepository.Sense> senses = wordnet.candidates(lemma, pennPos);
        List<Candidate> ranked = new ArrayList<>();
        for (var sense : senses) {
            try {
                ranked.add(new Candidate(sense.synset(), model.score(context, sense.synset().getGloss()), sense.senseKeys()));
            } catch (Exception e) {
                throw new IllegalStateException("graded-wsd scoring failed for " + lemma + ": " + e.getMessage(), e);
            }
        }
        ranked.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(c -> c.synset().getID().toString()));
        Candidate selected = select(ranked, threshold, margin);
        return new Result(ranked, selected, lemma, pennPos, context);
    }

    static Candidate select(List<Candidate> ranked, double threshold, double margin) {
        if (ranked.isEmpty() || ranked.get(0).score() < threshold) {
            return null;
        }
        if (ranked.size() > 1 && (ranked.get(0).score() <= ranked.get(1).score()
                || ranked.get(0).score() - ranked.get(1).score() < margin)) {
            return null;
        }
        return ranked.get(0);
    }

    public Map<String, Object> describe(Result result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kex:wsdMethod", METHOD);
        data.put("kex:wsdModel", GradedWsdModel.MODEL_ID);
        data.put("kex:wsdModelDirectory", model.directory().toString());
        data.put("kex:wsdScoreType", "raw-applicability-regression");
        data.put("kex:wsdTargetLemma", result.lemma());
        data.put("kex:wsdTargetPos", result.pos());
        data.put("kex:wsdTargetText", result.context().surface());
        data.put("kex:wsdContextText", result.context().text());
        data.put("kex:wsdTargetBegin", result.context().begin());
        data.put("kex:wsdTargetEnd", result.context().end());
        data.put("kex:wsdOffsetUnit", "UTF-16 relative to wsdContextText; end exclusive");
        data.put("kex:wsdThreshold", threshold);
        data.put("kex:wsdMargin", margin);
        data.put("kex:wsdValidated", false);
        data.put("kex:wordnetVersion", wordnet.version());
        data.put("kex:wsdStatus", result.candidates().isEmpty() ? "unlinked" : result.selected() == null ? "ambiguous" : "resolved");
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (var candidate : result.candidates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kex:synsetId", candidate.synset().getID().toString());
            item.put("kex:senseKey", new TreeSet<>(candidate.senseKeys()));
            item.put("kex:applicabilityScore", candidate.score());
            item.put("kex:definition", candidate.synset().getGloss());
            candidates.add(item);
        }
        if (!candidates.isEmpty()) {
            data.put("kex:senseCandidate", candidates);
        }
        if (result.selected() != null) {
            data.put("kex:selectedSynset", result.selected().synset().getID().toString());
            data.put("kex:selectedSenseKey", new TreeSet<>(result.selected().senseKeys()));
            if (omw != null) try {
                List<String> labels = omw.getTranslations(result.selected().synset(), language);
                if (!labels.isEmpty()) {
                    data.put("kex:translatedLabel", labels.stream()
                            .map(label -> Map.of("@value", label, "@language", languageTag(language))).toList());
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Cannot read OMW translations", e);
            }
        }
        return data;
    }

    private static String languageTag(String language) {
        return switch (language) {
            case "ita" ->
                "it";
            case "eng" ->
                "en";
            case "fra" ->
                "fr";
            case "deu" ->
                "de";
            case "spa" ->
                "es";
            default ->
                language;
        };
    }

    @Override
    public void close() {
        Exception failure = null;
        try {
            model.close();
        } catch (Exception e) {
            failure = e;
        }
        try {
            if (omw != null) {
                omw.close();

            }
        } catch (Exception e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        } finally {
            wordnet.close();
        }
        if (failure != null) {
            throw new IllegalStateException("Cannot close WSD resources", failure);
        }
    }
}

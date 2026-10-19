package com.kextractor.triples.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;

public final class VerbNetRoles {

    private static VerbNetLexicon configuredIndex;
    private static boolean loaded;

    public static synchronized VerbNetLexicon index() {
        if (!loaded) {
            String configured = System.getProperty("kextractor.verbnet.dir", System.getenv("KEX_VERBNET_DIR"));
            if (configured != null && !configured.isBlank() && !configured.equalsIgnoreCase("off")) {
                configuredIndex = VerbNetLexicon.load(Path.of(configured)); 
            }else if (configured == null || configured.isBlank()) {
                for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
                    Path path = base.resolve("verbnet/verbnet3.4");
                    if (Files.isDirectory(path)) {
                        configuredIndex = VerbNetLexicon.load(path);
                        break;
                    }
                }
            }
            loaded = true;
        }
        return configuredIndex;
    }

    static List<VerbNetLexicon.Match> match(String lemma, int predicate, boolean passive,
            List<JsonNode> edges, Map<Integer, JsonNode> tokens) {
        if (index() == null) {
            return List.of();
        }
        return index().match(lemma, arguments(predicate, passive, edges, tokens));
    }

    static List<VerbNetLexicon.Argument> arguments(int predicate, boolean passive,
            List<JsonNode> edges, Map<Integer, JsonNode> tokens) {
        List<VerbNetLexicon.Argument> args = new ArrayList<>();
        List<Integer> direct = new ArrayList<>(), indirect = new ArrayList<>(), promoted = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        boolean subject = false;
        for (JsonNode e : edges) {
            if (e.path("governor").asInt() != predicate) {
                continue;
            }
            String dep = e.path("dep").asText();
            int token = e.path("dependent").asInt();
            if (!seen.add(token)) {
                continue;
            }
            if (dep.startsWith("nsubj")) {
                if (passive) {
                    promoted.add(token); 
                }else {
                    args.add(argument(token, "subject", "", edges, tokens));
                    subject = true;
                }
            } else if (Set.of("obj", "dobj").contains(dep)) {
                direct.add(token); 
            }else if (dep.equals("iobj")) {
                indirect.add(token); 
            }else if (dep.startsWith("obl") || dep.startsWith("nmod")) {
                String prep = marker(token, "case", edges, tokens);
                if (prep.isBlank() && dep.contains(":")) {
                    prep = dep.substring(dep.indexOf(':') + 1);
                }
                if (passive && (prep.equals("by") || prep.equals("agent"))) {
                    args.add(argument(token, "subject", "", edges, tokens));
                    subject = true;
                } else {
                    args.add(argument(token, "oblique", prep, edges, tokens));
                }
            } else if (Set.of("ccomp", "xcomp", "csubj", "csubj:pass").contains(dep)) {
                var clause = argument(token, dep.startsWith("csubj") ? "csubj" : dep, marker(token, "mark", edges, tokens), edges, tokens);
                Set<String> features = new HashSet<>(clause.features());
                Set<Integer> controllers = new HashSet<>();
                for (JsonNode child : edges) {
                    if (child.path("governor").asInt() == token && child.path("dep").asText().startsWith("nsubj")) {
                        controllers.add(child.path("dependent").asInt());
                    }
                }
                for (JsonNode matrix : edges) {
                    if (matrix.path("governor").asInt() == predicate && controllers.contains(matrix.path("dependent").asInt())) {
                        String relation = matrix.path("dep").asText();
                        if (relation.startsWith("nsubj")) {
                            features.add(passive ? "object-controlled" : "subject-controlled");
                        }
                        if (Set.of("obj", "dobj", "iobj").contains(relation)) {
                            features.add("object-controlled");
                        }
                        if (passive && (relation.startsWith("obl") || relation.startsWith("nmod"))
                                && (relation.endsWith(":agent") || marker(matrix.path("dependent").asInt(), "case", edges, tokens).equals("by"))) {
                            features.add("subject-controlled");
                        }
                    }
                }
                args.add(new VerbNetLexicon.Argument(token, clause.slot(), clause.preposition(), features));
                if (dep.startsWith("csubj")) {
                    subject = true;
                }
            } else if (dep.equals("compound:prt") || dep.equals("prt")) {
                args.add(argument(token, "particle", tokens.get(token).path("lemma").asText().toLowerCase(Locale.ROOT), edges, tokens));
            } else if (dep.equals("expl")) {
                args.add(argument(token, "existential", "", edges, tokens));
            }
        }
        if (passive && !subject) {
            args.add(new VerbNetLexicon.Argument(-1, "subject", ""));
        }
        Collections.sort(direct);
        Collections.sort(indirect);
        Collections.sort(promoted);
        List<Integer> objects = new ArrayList<>(indirect);
        if (passive) {
            objects.addAll(promoted);
            objects.addAll(direct);
        } else {
            objects.addAll(direct);
        }
        for (int i = 0; i < objects.size(); i++) {
            args.add(argument(objects.get(i), "object" + (i + 1), "", edges, tokens));
        }
        return List.copyOf(args);
    }

    private static VerbNetLexicon.Argument argument(int token, String slot, String prep,
            List<JsonNode> edges, Map<Integer, JsonNode> tokens) {
        JsonNode word = tokens.get(token);
        Set<String> features = new HashSet<>();
        String pos = word == null ? "" : word.path("pos").asText();
        String lemma = word == null ? "" : word.path("lemma").asText().toLowerCase(Locale.ROOT);
        if (Set.of("NNS", "NNPS").contains(pos) || Set.of("we", "they", "us", "them").contains(lemma)
                || edges.stream().anyMatch(e -> e.path("governor").asInt() == token && e.path("dep").asText().startsWith("conj"))) {
            features.add("plural");
        }
        if (Set.of("myself", "yourself", "himself", "herself", "itself", "ourselves", "yourselves", "themselves").contains(lemma)) {
            features.add("reflexive");
        }
        if (pos.equals("VBG")) {
            features.add("gerund");
        }
        if (slot.equals("xcomp")) {
            if (prep.equals("to")) {
                features.add("infinitive"); 
            }else if (pos.equals("VB")) {
                features.add("bare-infinitive");
            }
        }
        return new VerbNetLexicon.Argument(token, slot, prep, features);
    }

    private static String marker(int head, String relation, List<JsonNode> edges, Map<Integer, JsonNode> tokens) {
        Set<Integer> words = new TreeSet<>();
        for (JsonNode e : edges) {
            if (e.path("governor").asInt() == head && e.path("dep").asText().equals(relation)) {
                words.add(e.path("dependent").asInt());
            }
        }
        boolean changed;
        do {
            changed = false;
            for (JsonNode e : edges) {
                if (words.contains(e.path("governor").asInt()) && e.path("dep").asText().equals("fixed")) {
                    changed |= words.add(e.path("dependent").asInt());
                }
            }
        } while (changed);
        return String.join(" ", words.stream().filter(tokens::containsKey).map(i -> tokens.get(i).path("lemma")
                .asText(tokens.get(i).path("word").asText()).toLowerCase(Locale.ROOT)).toList());
    }

    static Map<Integer, String> consensus(List<VerbNetLexicon.Match> matches) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> result = new HashMap<>(matches.get(0).roles());
        for (var match : matches) {
            result.entrySet().removeIf(e -> !e.getValue().equals(match.roles().get(e.getKey())));
        }
        return result;
    }
}

package com.kextractor.triples.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SentenceStructure {

    final List<JsonNode> tokens = new ArrayList<>();
    final Map<Integer, JsonNode> byIndex = new LinkedHashMap<>();
    final List<JsonNode> basic = new ArrayList<>();
    final List<JsonNode> arguments = new ArrayList<>();
    final Set<Integer> predicates = new LinkedHashSet<>();
    private final Map<String, String> sources = new HashMap<>();

    SentenceStructure(JsonNode sentence) {
        sentence.path("tokens").forEach(t -> {
            tokens.add(t);
            byIndex.put(t.path("index").asInt(), t);
        });
        JsonNode base = sentence.has("basicDependencies") ? sentence.path("basicDependencies")
                : sentence.path("enhancedPlusPlusDependencies");
        base.forEach(basic::add);
        for (JsonNode token : tokens) {
            int index = token.path("index").asInt();
            boolean auxiliary = basic.stream().anyMatch(e -> dependent(e) == index
                    && (relation(e).startsWith("aux") || relation(e).equals("cop")));
            if (!auxiliary && (token.path("pos").asText().startsWith("VB") || copular(index))) {
                predicates.add(index);
            }
        }
        for (JsonNode edge : basic) {
            arguments.add(edge);
            sources.put(key(edge), "basic-dependency");
        }
        JsonNode enhanced = sentence.has("enhancedPlusPlusDependencies") ? sentence.path("enhancedPlusPlusDependencies")
                : sentence.path("enhancedDependencies");
        Map<Integer, Integer> antecedents = new HashMap<>();
        for (JsonNode edge : enhanced) {
            if (relation(edge).equals("ref")) {
                antecedents.put(dependent(edge), governor(edge));
            }
        }
        for (JsonNode edge : enhanced) {
            String dep = relation(edge);
            if (!predicates.contains(governor(edge)) || !byIndex.containsKey(dependent(edge))
                    || !edge.path("governor").isIntegralNumber() || !edge.path("dependent").isIntegralNumber()
                    || edge.path("governorCopy").asInt() != 0 || edge.path("dependentCopy").asInt() != 0
                    || !nominalRelation(dep) || sources.containsKey(key(edge))) {
                continue;
            }
            boolean grouped = arguments.stream().anyMatch(old -> governor(old) == governor(edge)
                    && dependent(old) != dependent(edge)
                    && baseRelation(relation(old)).equals(baseRelation(dep))
                    && nominalConjuncts(dependent(old)).contains(dependent(edge)));
            if (grouped) {
                continue;
            }
            List<JsonNode> replaced = arguments.stream().filter(old -> governor(old) == governor(edge)
                    && baseRelation(relation(old)).equals(baseRelation(dep))
                    && Objects.equals(antecedents.get(dependent(old)), dependent(edge))).toList();
            boolean conflictingSubject = dep.startsWith("nsubj") && arguments.stream().anyMatch(old
                    -> governor(old) == governor(edge) && relation(old).startsWith("nsubj")
                    && dependent(old) != dependent(edge) && !replaced.contains(old));
            if (conflictingSubject) {
                continue;
            }
            arguments.removeAll(replaced);
            arguments.removeIf(old -> governor(old) == governor(edge) && dependent(old) == dependent(edge)
                    && baseRelation(relation(old)).equals(baseRelation(dep)));
            arguments.add(edge);
            sources.put(key(edge), replaced.isEmpty() ? "enhanced-dependency" : "enhanced-relative-reference");
        }
        shareCoordinatedSubjects();
        arguments.sort(Comparator.comparingInt(SentenceStructure::governor)
                .thenComparingInt(SentenceStructure::dependent).thenComparing(SentenceStructure::relation));
    }

    private void shareCoordinatedSubjects() {
        boolean changed;
        do {
            changed = false;
            for (JsonNode conjunction : basic) {
                int parent = governor(conjunction), child = dependent(conjunction);
                if (!baseRelation(relation(conjunction)).equals("conj") || !predicates.contains(parent)
                        || !predicates.contains(child) || arguments.stream().anyMatch(e -> governor(e) == child
                        && (relation(e).startsWith("nsubj") || relation(e).startsWith("csubj") || relation(e).equals("expl")))) {
                    continue;
                }
                List<JsonNode> subjects = arguments.stream().filter(e -> governor(e) == parent
                        && relation(e).startsWith("nsubj")).toList();
                boolean childPassive = passive(child) || passive(parent) && byIndex.get(child).path("pos").asText().equals("VBN");
                for (JsonNode subject : subjects) {
                    ObjectNode inherited = subject.deepCopy();
                    inherited.put("governor", child).put("governorGloss", byIndex.get(child).path("word").asText());
                    inherited.put("dep", childPassive ? "nsubj:pass" : "nsubj");
                    arguments.add(inherited);
                    sources.put(key(inherited), "shared-coordination-subject");
                    changed = true;
                }
            }
        } while (changed);
    }

    boolean copular(int predicate) {
        return basic.stream().anyMatch(e -> governor(e) == predicate && relation(e).equals("cop"));
    }

    boolean passive(int predicate) {
        return arguments.stream().anyMatch(e -> governor(e) == predicate
                && (relation(e).equals("nsubjpass") || relation(e).equals("nsubj:pass")
                || relation(e).equals("csubj:pass") || relation(e).equals("auxpass") || relation(e).equals("aux:pass")
                || relation(e).equals("obl:agent") || relation(e).equals("nmod:agent")));
    }

    boolean participialModifier(int predicate) {
        JsonNode edge = incoming(predicate);
        return edge != null && Set.of("amod", "acl").contains(relation(edge))
                && Set.of("VBN", "VBG").contains(byIndex.get(predicate).path("pos").asText());
    }

    String source(JsonNode edge) {
        return sources.getOrDefault(key(edge), "basic-dependency");
    }

    List<JsonNode> outgoing(int predicate) {
        return arguments.stream().filter(e -> governor(e) == predicate).toList();
    }

    JsonNode incoming(int token) {
        return basic.stream().filter(e -> dependent(e) == token).findFirst().orElse(null);
    }

    String marker(int head, String dep) {
        Set<Integer> marked = new TreeSet<>();
        for (JsonNode edge : basic) {
            if (governor(edge) == head && baseRelation(relation(edge)).equals(dep)) {
                marked.add(dependent(edge));
                collectFixed(dependent(edge), marked);
            }
        }
        return String.join(" ", marked.stream().map(i -> byIndex.get(i).path("lemma")
                .asText(byIndex.get(i).path("word").asText()).toLowerCase(Locale.ROOT)).toList());
    }

    private void collectFixed(int head, Set<Integer> marked) {
        for (JsonNode edge : basic) {
            if (governor(edge) == head && relation(edge).equals("fixed")
                    && marked.add(dependent(edge))) {
                collectFixed(dependent(edge), marked);
            }
        }
    }

    List<Integer> nominalConjuncts(int head) {
        Set<Integer> members = new TreeSet<>();
        members.add(head);
        String preposition = marker(head, "case");
        boolean changed;
        do {
            changed = false;
            for (JsonNode edge : basic) {
                if (members.contains(governor(edge)) && baseRelation(relation(edge)).equals("conj")
                        && !predicates.contains(dependent(edge))
                        && (marker(dependent(edge), "case").isBlank() || marker(dependent(edge), "case").equals(preposition))) {
                    changed |= members.add(dependent(edge));
                }
            }
        } while (changed);
        return List.copyOf(members);
    }

    Set<Integer> phrase(int head, boolean clause) {
        Set<Integer> result = new TreeSet<>();
        collectPhrase(head, clause, result);
        return result;
    }

    private void collectPhrase(int head, boolean clause, Set<Integer> result) {
        if (!byIndex.containsKey(head) || !result.add(head)) {
            return;
        }
        for (JsonNode edge : basic) {
            if (governor(edge) == head && (clause || Set.of("compound", "flat", "name",
                    "amod", "det", "nummod", "nmod:poss", "poss").contains(relation(edge)))) {
                collectPhrase(dependent(edge), clause, result);
            }
        }
    }

    static boolean nominalRelation(String dep) {
        return dep.startsWith("nsubj") || Set.of("obj", "dobj", "iobj").contains(dep)
                || dep.startsWith("obl") || dep.startsWith("nmod");
    }

    static String baseRelation(String dep) {
        if (dep.equals("nsubjpass")) {
            return "nsubj";
        }
        if (dep.equals("dobj")) {
            return "obj";
        }
        if (dep.equals("auxpass")) {
            return "aux";
        }
        int colon = dep.indexOf(':');
        return colon < 0 ? dep : dep.substring(0, colon);
    }

    static int governor(JsonNode edge) {
        return edge.path("governor").asInt();
    }

    static int dependent(JsonNode edge) {
        return edge.path("dependent").asInt();
    }

    static String relation(JsonNode edge) {
        return edge.path("dep").asText();
    }

    private static String key(JsonNode edge) {
        return governor(edge) + "/" + relation(edge) + "/" + dependent(edge);
    }
}

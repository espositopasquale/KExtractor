package com.kextractor.triples.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class VerbNetLexicon {

    public record Argument(int token, String slot, String preposition, Set<String> features) {

        public Argument(int token, String slot, String preposition) {
            this(token, slot, preposition, Set.of());
        }

        public Argument {
            features = Set.copyOf(features);
        }
    }

    public record Match(String classId, String frameId, String description, Map<Integer, String> roles,
            String syntax, List<Integer> unmatchedArguments, int matchedArgumentCount,
            boolean syntacticRestrictionsEvaluated) {}

    private record Restriction(String type, boolean positive) {}

    private record Slot(String position, Set<String> prepositions, String role, List<Restriction> restrictions) {}

    private record Frame(String owner, String id, String description, String syntax, List<Slot> slots) {}
    private final Map<String, List<Frame>> index = new HashMap<>();
    private final Map<String, Set<String>> senseKeys = new HashMap<>();
    private int classes;

    private VerbNetLexicon() {
    }

    public int classCount() {
        return classes;
    }

    public static VerbNetLexicon load(Path directory) {
        VerbNetLexicon lexicon = new VerbNetLexicon();
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                    Element root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                    if (root.getTagName().equals("VNCLASS")) {
                        lexicon.readClass(root, List.of());
                    }
                }
            }
            if (lexicon.classes == 0 || lexicon.index.isEmpty()) {
                throw new IllegalArgumentException("No VerbNet classes found");
            }
            return lexicon;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot load VerbNet from " + directory + ": " + e.getMessage(), e);
        }
    }

    private void readClass(Element cls, List<Frame> inherited) {
        classes++;
        List<Frame> frames = new ArrayList<>(inherited);
        int ordinal = 0;
        for (Element frame : children(child(cls, "FRAMES"), "FRAME")) {
            ordinal++;
            List<Slot> slots = slots(child(frame, "SYNTAX"));
            Element description = child(frame, "DESCRIPTION");
            if (slots != null) {
                frames.add(new Frame(cls.getAttribute("ID"), cls.getAttribute("ID") + "#" + ordinal,
                        description == null ? "" : description.getAttribute("primary"), syntax(child(frame, "SYNTAX")), slots));
            }
        }
        for (Element member : children(child(cls, "MEMBERS"), "MEMBER")) {
            String lemma = normalizeLemma(member.getAttribute("name"));
            Set<String> keys = senseKeys.computeIfAbsent(lemma + "/" + cls.getAttribute("ID"), k -> new HashSet<>());
            for (String key : member.getAttribute("wn").split("\\s+")) {
                if (!key.isBlank()) {
                    keys.add(key.replaceFirst("[?]$", ""));
                }
            }
            List<Frame> target = index.computeIfAbsent(lemma, k -> new ArrayList<>());
            for (Frame frame : frames) {
                target.add(new Frame(cls.getAttribute("ID"), frame.id, frame.description, frame.syntax, frame.slots));
            }
        }
        for (Element sub : children(child(cls, "SUBCLASSES"), "VNSUBCLASS")) {
            readClass(sub, frames);
        }
    }

    private static List<Slot> slots(Element syntax) {
        if (syntax == null) {
            return null;
        }
        List<Slot> result = new ArrayList<>();
        List<Element> parts = children(syntax, null);
        boolean verb = false;
        Set<String> prep = Set.of();
        int objects = 0;
        for (int i = 0; i < parts.size(); i++) {
            Element part = parts.get(i);
            switch (part.getTagName()) {
                case "VERB" -> {
                    if (verb) {
                        return null;
                    
                    }verb = true;
                }
                case "PREP" -> {
                    prep = prepositions(part);
                    if (prep == null || prep.isEmpty()) {
                        return null;
                    }
                }
                case "LEX" -> {
                    Set<String> words = words(part.getAttribute("value"));
                    if (words.isEmpty() || !verb) {
                        return null;
                    }
                    if (i + 1 < parts.size() && parts.get(i + 1).getTagName().equals("NP")) {
                        prep = words; 
                    }else {
                        result.add(new Slot("particle", words, "", List.of()));
                    }
                }
                case "NP", "VP" -> {
                    String role = part.getAttribute("value");
                    if (role.isBlank()) {
                        return null;
                    }
                    List<Restriction> restrictions = restrictions(part);
                    if (restrictions == null) {
                        return null;
                    }
                    boolean clause = part.getTagName().equals("VP") || restrictions.stream().anyMatch(r -> r.positive && clausal(r.type));
                    String position = !verb ? clause ? "clause-subject" : "subject"
                            : !prep.isEmpty() ? "oblique" : clause ? "clause" : "object" + (++objects);
                    result.add(new Slot(position, prep, Character.toLowerCase(role.charAt(0)) + role.substring(1), restrictions));
                    prep = Set.of();
                }
                default -> {
                    return null;
                }
            }
        }
        if (!verb || !prep.isEmpty() || result.isEmpty()) {
            return null;
        }
        return List.copyOf(result);
    }

    private static List<Restriction> restrictions(Element part) {
        List<Restriction> result = new ArrayList<>();
        Element restrictionGroup = child(part, "SYNRESTRS");
        if (restrictionGroup != null && restrictionGroup.getAttribute("logic").equals("or")) {
            return null;
        }
        for (Element r : children(restrictionGroup, "SYNRESTR")) {
            String type = r.getAttribute("type");
            if (!Set.of("sentential", "that_comp", "sc_to_inf", "oc_to_inf",
                    "oc_bare_inf", "sc_ing", "oc_ing", "plural", "refl").contains(type)) {
                return null;
            }
            result.add(new Restriction(type, r.getAttribute("Value").equals("+")));
        }
        return List.copyOf(result);
    }

    private static boolean clausal(String type) {
        return !Set.of("plural", "refl").contains(type);
    }

    private static Set<String> words(String value) {
        if (value.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(value.toLowerCase(Locale.ROOT).trim().split("\\s+")));
    }

    private static Set<String> prepositions(Element part) {
        if (!part.getAttribute("value").isBlank()) {
            return words(part.getAttribute("value"));
        }
        Set<String> result = null;
        Element group = child(part, "SELRESTRS");
        if (group == null || group.getAttribute("logic").equals("or")) {
            return null;
        }
        for (Element r : children(group, "SELRESTR")) {
            Set<String> values = switch (r.getAttribute("type")) {
                case "src", "src_conf" ->
                    Set.of("from", "out_of", "off");
                case "dest", "dest_dir", "gol" ->
                    Set.of("to", "toward", "towards", "into", "onto");
                case "dest_conf" ->
                    Set.of("in", "into", "on", "onto");
                case "dir" ->
                    Set.of("to", "toward", "towards", "from", "into", "onto", "out_of", "off");
                case "path" ->
                    Set.of("across", "along", "around", "down", "over", "past", "through", "toward", "towards", "up", "into", "out_of");
                case "loc", "spatial" ->
                    Set.of("at", "in", "on", "under", "over", "above", "below", "behind", "beside", "between", "near", "inside", "outside", "around", "through", "across", "along", "into", "onto", "from", "to", "off");
                case "at" ->
                    Set.of("at");
                default ->
                    null;
            };
            if (values == null) {
                return null;
            }
            if (r.getAttribute("Value").equals("-")) {
                if (result == null) {
                    return null;
                }
                result.removeAll(values);
            } else if (result == null) {
                result = new HashSet<>(values); 
            }else {
                result.retainAll(values);
            }
        }
        return result == null ? null : Set.copyOf(result);
    }

    public List<Match> match(String lemma, List<Argument> arguments) {
        List<Match> matches = new ArrayList<>();
        int bestCoverage = -1, bestSpecificity = -1;
        for (Frame frame : index.getOrDefault(normalizeLemma(lemma), List.of())) {
            Map<Integer, String> roles = new TreeMap<>();
            Set<Integer> used = new HashSet<>();
            boolean compatible = true;
            int specificity = 0;
            for (Slot slot : frame.slots) {
                Argument found = null;
                for (Argument arg : arguments) {
                    if (!used.contains(arg.token) && accepts(slot, arg)) {
                        found = arg;
                        break;
                    }
                }
                if (found == null) {
                    compatible = false;
                    break;
                }
                used.add(found.token);
                if (found.token > 0 && !slot.role.isBlank()) {
                    roles.put(found.token, slot.role);
                }
                if (!slot.prepositions.isEmpty()) {
                    specificity++;
                }
            }
            if (!compatible) {
                continue;
            }
            List<Integer> unmatched = new ArrayList<>();
            for (Argument arg : arguments) {
                if (!used.contains(arg.token)) {
                    if (!arg.slot.equals("oblique") && !arg.slot.equals("adjunct")) {
                        compatible = false;
                        break;
                    }
                    if (arg.token > 0) {
                        unmatched.add(arg.token);
                    }
                }
            }
            if (!compatible) {
                continue;
            }
            int coverage = (int) arguments.stream().filter(a -> a.token > 0 && used.contains(a.token)).count();
            if (coverage < bestCoverage || coverage == bestCoverage && specificity < bestSpecificity) {
                continue;
            }
            if (coverage > bestCoverage || specificity > bestSpecificity) {
                matches.clear();
                bestCoverage = coverage;
                bestSpecificity = specificity;
            }
            matches.add(new Match(frame.owner, frame.id, frame.description, Map.copyOf(roles), frame.syntax,
                    List.copyOf(unmatched), coverage, true));
        }
        return List.copyOf(matches);
    }

    private static boolean accepts(Slot slot, Argument arg) {
        boolean position = slot.position.equals(arg.slot)
                || slot.position.equals("clause") && Set.of("ccomp", "xcomp").contains(arg.slot)
                || slot.position.equals("clause-subject") && arg.slot.equals("csubj");
        if (!position || !slot.prepositions.isEmpty() && !slot.prepositions.contains(arg.preposition.replace(' ', '_'))) {
            return false;
        }
        for (Restriction restriction : slot.restrictions) {
            String type = restriction.type;
            boolean satisfied = switch (type) {
                case "sentential" ->
                    Set.of("ccomp", "xcomp", "csubj").contains(arg.slot);
                case "that_comp" ->
                    arg.slot.equals("ccomp") && (arg.preposition.isBlank() || arg.preposition.equals("that"));
                case "sc_to_inf" ->
                    arg.slot.equals("xcomp") && arg.features.containsAll(Set.of("infinitive", "subject-controlled"));
                case "oc_to_inf" ->
                    arg.slot.equals("xcomp") && arg.features.containsAll(Set.of("infinitive", "object-controlled"));
                case "oc_bare_inf" ->
                    arg.slot.equals("xcomp") && arg.features.containsAll(Set.of("bare-infinitive", "object-controlled"));
                case "sc_ing" ->
                    Set.of("ccomp", "xcomp").contains(arg.slot) && arg.features.containsAll(Set.of("gerund", "subject-controlled"));
                case "oc_ing" ->
                    Set.of("ccomp", "xcomp").contains(arg.slot) && arg.features.containsAll(Set.of("gerund", "object-controlled"));
                case "plural" ->
                    arg.features.contains("plural");
                case "refl" ->
                    arg.features.contains("reflexive");
                default ->
                    false;
            };
            if (satisfied != restriction.positive) {
                return false;
            }
        }
        return true;
    }

    public boolean supportsSense(String lemma, String classId, Set<String> keys) {
        Set<String> mapped = senseKeys.getOrDefault(normalizeLemma(lemma) + "/" + classId, Set.of());
        for (String full : keys) {
            String[] parts = full.split(":", -1);
            if (parts.length >= 3 && mapped.contains(parts[0] + ":" + parts[1] + ":" + parts[2])) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeLemma(String lemma) {
        return lemma.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String syntax(Element syntax) {
        List<String> result = new ArrayList<>();
        for (Element part : children(syntax, null)) {
            result.add(part.getTagName()
                    + (part.getAttribute("value").isBlank() ? "" : "[" + part.getAttribute("value") + "]"));
        }
        return String.join(" ", result);
    }

    private static Element child(Element parent, String name) {
        return children(parent, name).stream().findFirst().orElse(null);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent != null) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && (name == null || e.getTagName().equals(name))) {
                    result.add(e);
                }
            }
        }
        return result;
    }
}

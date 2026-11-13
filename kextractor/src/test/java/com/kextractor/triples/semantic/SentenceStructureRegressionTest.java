package com.kextractor.triples.semantic;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SentenceStructureRegressionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void propagatedCoordinatedObjectIsOneArgumentGroupNotADitransitive() {
        var sentence = sentence(new String[]{"Alice", "approved", "contracts", "and", "budgets"},
                new String[]{"NNP", "VBD", "NNS", "CC", "NNS"});
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "obj", 2, 3);
        edge(sentence, "basicDependencies", "conj", 3, 5);
        edge(sentence, "basicDependencies", "cc", 5, 4);
        edge(sentence, "enhancedPlusPlusDependencies", "obj", 2, 5);
        var syntax = new SentenceStructure(sentence);
        assertEquals(List.of(3), dependents(syntax, 2, "obj"));
        assertEquals(List.of(3, 5), syntax.nominalConjuncts(3));
    }

    @Test
    void propagatedCoordinatedObliqueIsOneArgumentGroup() {
        var sentence = sentence(new String[]{"Alice", "travelled", "to", "Rome", "and", "Paris"},
                new String[]{"NNP", "VBD", "IN", "NNP", "CC", "NNP"});
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "obl", 2, 4);
        edge(sentence, "basicDependencies", "case", 4, 3);
        edge(sentence, "basicDependencies", "conj", 4, 6);
        edge(sentence, "basicDependencies", "cc", 6, 5);
        edge(sentence, "enhancedPlusPlusDependencies", "obl:to", 2, 4);
        edge(sentence, "enhancedPlusPlusDependencies", "obl:to", 2, 6);
        var syntax = new SentenceStructure(sentence);
        assertEquals(List.of(4), dependents(syntax, 2, "obl"));
        assertEquals(List.of(4, 6), syntax.nominalConjuncts(4));
    }

    @Test
    void aDistinctExplicitPrepositionKeepsItsOwnObliqueArgument() {
        var sentence = sentence(new String[]{"Alice", "travelled", "from", "Rome", "and", "to", "Paris"},
                new String[]{"NNP", "VBD", "IN", "NNP", "CC", "IN", "NNP"});
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "obl", 2, 4);
        edge(sentence, "basicDependencies", "case", 4, 3);
        edge(sentence, "basicDependencies", "conj", 4, 7);
        edge(sentence, "basicDependencies", "cc", 7, 5);
        edge(sentence, "basicDependencies", "case", 7, 6);
        edge(sentence, "enhancedPlusPlusDependencies", "obl:from", 2, 4);
        edge(sentence, "enhancedPlusPlusDependencies", "obl:to", 2, 7);
        var syntax = new SentenceStructure(sentence);
        assertEquals(List.of(4, 7), dependents(syntax, 2, "obl"));
        assertEquals(List.of(4), syntax.nominalConjuncts(4));
        assertEquals("from", syntax.marker(4, "case"));
        assertEquals("to", syntax.marker(7, "case"));
    }

    @Test
    void universalPassiveSubjectReplacesTheLegacyStanfordLabel() {
        var sentence = sentence(new String[]{"The", "device", "was", "tested"},
                new String[]{"DT", "NN", "VBD", "VBN"});
        edge(sentence, "basicDependencies", "ROOT", 0, 4);
        edge(sentence, "basicDependencies", "det", 2, 1);
        edge(sentence, "basicDependencies", "nsubjpass", 4, 2);
        edge(sentence, "basicDependencies", "auxpass", 4, 3);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj:pass", 4, 2);
        var syntax = new SentenceStructure(sentence);
        var subjects = syntax.outgoing(4).stream()
                .filter(e -> SentenceStructure.relation(e).startsWith("nsubj")).toList();
        assertEquals(1, subjects.size());
        assertEquals("nsubj:pass", SentenceStructure.relation(subjects.get(0)));
        assertTrue(syntax.passive(4));
    }

    @Test
    void explicitEnhancedPassiveSubjectMarksCoordinatedPredicatePassive() {
        var sentence = sentence(new String[]{"The", "device", "was", "assembled", "and", "tested"},
                new String[]{"DT", "NN", "VBD", "VBN", "CC", "VBN"});
        edge(sentence, "basicDependencies", "ROOT", 0, 4);
        edge(sentence, "basicDependencies", "nsubj:pass", 4, 2);
        edge(sentence, "basicDependencies", "aux:pass", 4, 3);
        edge(sentence, "basicDependencies", "conj", 4, 6);
        edge(sentence, "basicDependencies", "cc", 6, 5);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj:pass", 6, 2);
        var syntax = new SentenceStructure(sentence);
        assertEquals(Set.of(4, 6), syntax.predicates);
        assertTrue(syntax.passive(6));
        assertEquals(List.of(2), dependents(syntax, 6, "nsubj"));
    }

    @Test
    void universalObjectReplacesTheLegacyDirectObjectLabel() {
        var sentence = sentence(new String[]{"Alice", "read", "books"}, new String[]{"NNP", "VBD", "NNS"});
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "dobj", 2, 3);
        edge(sentence, "enhancedPlusPlusDependencies", "obj", 2, 3);
        var syntax = new SentenceStructure(sentence);
        assertEquals(1, syntax.outgoing(2).size());
        assertEquals("obj", SentenceStructure.relation(syntax.outgoing(2).get(0)));
    }

    private List<Integer> dependents(SentenceStructure syntax, int predicate, String relation) {
        return syntax.outgoing(predicate).stream().filter(e -> SentenceStructure.baseRelation(
                SentenceStructure.relation(e)).equals(relation)).map(SentenceStructure::dependent).toList();
    }

    private ObjectNode sentence(String[] words, String[] pos) {
        var sentence = mapper.createObjectNode();
        var tokens = sentence.putArray("tokens");
        for (int i = 0; i < words.length; i++) {
            tokens.addObject().put("index", i + 1).put("word", words[i])
                    .put("lemma", words[i].toLowerCase(Locale.ROOT)).put("pos", pos[i]);
        }
        sentence.putArray("basicDependencies");
        sentence.putArray("enhancedPlusPlusDependencies");
        return sentence;
    }

    private void edge(ObjectNode sentence, String field, String relation, int governor, int dependent) {
        ((ArrayNode) sentence.path(field)).addObject().put("dep", relation)
                .put("governor", governor).put("dependent", dependent);
    }
}

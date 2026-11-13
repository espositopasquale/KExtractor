package com.kextractor.triples.semantic;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SentenceStructureTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enhancedControlKeepsTheExplicitController() {
        var sentence = sentence("Alice", "persuaded", "Bob", "to", "leave");
        tags(sentence, "NNP", "VBD", "NNP", "TO", "VB");
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "obj", 2, 3);
        edge(sentence, "basicDependencies", "xcomp", 2, 5);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj:xsubj", 5, 3);
        var syntax = new SentenceStructure(sentence);
        assertEquals(Set.of(2, 5), syntax.predicates);
        var subject = syntax.outgoing(5).get(0);
        assertEquals(3, SentenceStructure.dependent(subject));
        assertEquals("enhanced-dependency", syntax.source(subject));
        assertFalse(syntax.outgoing(5).stream().anyMatch(e -> SentenceStructure.dependent(e) == 1));
    }

    @Test
    void finiteSubjectOverridesAnIncompatiblePropagatedSubject() {
        var sentence = sentence("Alice", "said", "Bob", "left");
        tags(sentence, "NNP", "VBD", "NNP", "VBD");
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "ccomp", 2, 4);
        edge(sentence, "basicDependencies", "nsubj", 4, 3);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj", 4, 1);
        var syntax = new SentenceStructure(sentence);
        assertEquals(List.of(3), syntax.outgoing(4).stream().map(SentenceStructure::dependent).toList());
    }

    @Test
    void enhancedRelativeAntecedentReplacesOnlyTheRelativePronoun() {
        var sentence = sentence("Alice", "who", "wrote", "left");
        tags(sentence, "NNP", "WP", "VBD", "VBD");
        edge(sentence, "basicDependencies", "ROOT", 0, 4);
        edge(sentence, "basicDependencies", "nsubj", 4, 1);
        edge(sentence, "basicDependencies", "acl:relcl", 1, 3);
        edge(sentence, "basicDependencies", "nsubj", 3, 2);
        edge(sentence, "enhancedPlusPlusDependencies", "ref", 1, 2);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj", 3, 1);
        var syntax = new SentenceStructure(sentence);
        assertEquals(1, syntax.outgoing(3).size());
        assertEquals(1, SentenceStructure.dependent(syntax.outgoing(3).get(0)));
        assertEquals("enhanced-relative-reference", syntax.source(syntax.outgoing(3).get(0)));
        assertEquals(Set.of(1), syntax.phrase(1, false));
    }

    @Test
    void auxiliaryAndNegationRemainInTheirOwnClause() {
        var sentence = sentence("Alice", "did", "not", "approve", "but", "signed");
        tags(sentence, "NNP", "VBD", "RB", "VB", "CC", "VBD");
        edge(sentence, "basicDependencies", "ROOT", 0, 4);
        edge(sentence, "basicDependencies", "aux", 4, 2);
        edge(sentence, "basicDependencies", "neg", 4, 3);
        edge(sentence, "basicDependencies", "conj", 4, 6);
        edge(sentence, "basicDependencies", "cc", 6, 5);
        var syntax = new SentenceStructure(sentence);
        assertEquals(Set.of(4, 6), syntax.predicates);
        assertFalse(syntax.outgoing(6).stream().anyMatch(e -> SentenceStructure.relation(e).equals("neg")));
        assertEquals("but", syntax.marker(6, "cc"));
    }

    @Test
    void disjunctiveNominalGroupExcludesCoordinatedVerbs() {
        var sentence = sentence("Alice", "or", "Bob", "wrote", "and", "signed");
        tags(sentence, "NNP", "CC", "NNP", "VBD", "CC", "VBD");
        edge(sentence, "basicDependencies", "ROOT", 0, 4);
        edge(sentence, "basicDependencies", "nsubj", 4, 1);
        edge(sentence, "basicDependencies", "conj", 1, 3);
        edge(sentence, "basicDependencies", "cc", 3, 2);
        edge(sentence, "basicDependencies", "conj", 4, 6);
        var syntax = new SentenceStructure(sentence);
        assertEquals(List.of(1, 3), syntax.nominalConjuncts(1));
        assertEquals(List.of(4), syntax.nominalConjuncts(4));
        assertEquals("or", syntax.marker(3, "cc"));
    }

    @Test
    void coordinationSharesAnExplicitlyControlledSubjectWithoutGuessingTheController() {
        var sentence = sentence("Alice", "told", "Bob", "to", "inspect", "and", "repair");
        tags(sentence, "NNP", "VBD", "NNP", "TO", "VB", "CC", "VB");
        edge(sentence, "basicDependencies", "ROOT", 0, 2);
        edge(sentence, "basicDependencies", "nsubj", 2, 1);
        edge(sentence, "basicDependencies", "obj", 2, 3);
        edge(sentence, "basicDependencies", "xcomp", 2, 5);
        edge(sentence, "basicDependencies", "conj", 5, 7);
        edge(sentence, "enhancedPlusPlusDependencies", "nsubj:xsubj", 5, 3);
        var syntax = new SentenceStructure(sentence);
        assertEquals(3, SentenceStructure.dependent(syntax.outgoing(7).get(0)));
        assertEquals("shared-coordination-subject", syntax.source(syntax.outgoing(7).get(0)));
        assertFalse(syntax.outgoing(7).stream().anyMatch(e -> SentenceStructure.dependent(e) == 1));
    }

    @Test
    void participialModifierKeepsItsNominalAttachment() {
        var sentence = sentence("signed", "report", "arrived");
        tags(sentence, "VBN", "NN", "VBD");
        edge(sentence, "basicDependencies", "ROOT", 0, 3);
        edge(sentence, "basicDependencies", "nsubj", 3, 2);
        edge(sentence, "basicDependencies", "amod", 2, 1);
        var syntax = new SentenceStructure(sentence);
        assertTrue(syntax.participialModifier(1));
        assertEquals(2, SentenceStructure.governor(syntax.incoming(1)));
        assertFalse(syntax.participialModifier(3));
        assertFalse(syntax.passive(1));
    }

    private ObjectNode sentence(String... words) {
        var sentence = mapper.createObjectNode();
        var tokens = sentence.putArray("tokens");
        for (int i = 0; i < words.length; i++) {
            tokens.addObject().put("index", i + 1).put("word", words[i]).put("lemma", words[i].toLowerCase(Locale.ROOT));
        }
        sentence.putArray("basicDependencies");
        sentence.putArray("enhancedPlusPlusDependencies");
        return sentence;
    }

    private void tags(ObjectNode sentence, String... tags) {
        for (int i = 0; i < tags.length; i++) {
            ((ObjectNode) sentence.path("tokens").get(i)).put("pos", tags[i]);
        }
    }

    private void edge(ObjectNode sentence, String field, String dep, int governor, int dependent) {
        ((ArrayNode) sentence.path(field)).addObject().put("dep", dep).put("governor", governor).put("dependent", dependent);
    }
}

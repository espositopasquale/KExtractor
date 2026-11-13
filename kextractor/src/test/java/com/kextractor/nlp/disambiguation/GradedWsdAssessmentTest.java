package com.kextractor.nlp.disambiguation;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GradedWsdAssessmentTest {

    @Test
    void tokenizerMatchesIndependentHuggingFaceReference() throws Exception {
        var mapper = new ObjectMapper();
        JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/wsd/tokenizer-reference.json")) {
            assertNotNull(input);
            fixture = mapper.readTree(input);
        }
        var tokenizerFile = GradedWsdModel.configuredDirectory().resolve("tokenizer.json");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(tokenizerFile)));
        assertEquals(fixture.path("tokenizerSha256").asText(), digest,
                "Tokenizer changed: regenerate the independent Hugging Face reference before assessing");
        var tokenizer = new GradedWsdTokenizer(tokenizerFile);
        for (JsonNode example : fixture.path("cases")) {
            long[] expected = new long[example.path("ids").size()];
            for (int i = 0; i < expected.length; i++) {
                expected[i] = example.path("ids").get(i).longValue();
            }
            assertArrayEquals(expected, tokenizer.encode(example.path("sentence").asText(),
                    example.path("definition").asText()), example.path("name").asText());
        }
    }

    @Test
    void tokenizerRejectsLostOrAmbiguousTargets() throws Exception {
        var tokenizer = new GradedWsdTokenizer(GradedWsdModel.configuredDirectory().resolve("tokenizer.json"));
        assertThrows(IllegalArgumentException.class, () -> tokenizer.encode("A bank.", "A financial institution."));
        assertThrows(IllegalArgumentException.class, () -> tokenizer.encode("<t></t>", "A financial institution."));
        assertThrows(IllegalArgumentException.class,
                () -> tokenizer.encode("<t>bank</t> beside a <t>bank</t>", "A financial institution."));
        assertThrows(IllegalArgumentException.class,
                () -> tokenizer.encode("word ".repeat(600) + "<t>bank</t>", "A financial institution."));
        assertThrows(IllegalArgumentException.class, () -> tokenizer.encode("A <t>bank</t>.", " "));
    }

    @Test
    void marksOnlyTheRequestedOccurrenceAfterUnicode() {
        String sentence = "🌊 A bank beside the river bank.";
        int begin = sentence.lastIndexOf("bank");
        var context = OccurrenceContext.of(sentence, begin, begin + 4);
        assertEquals("🌊 A bank beside the river <t>bank</t>.", context.markedText());
        assertEquals("bank", context.surface());
        assertThrows(IllegalArgumentException.class, () -> OccurrenceContext.of(sentence, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> OccurrenceContext.of("<t>bank</t>", 3, 7));
    }

    @Test
    void parserOffsetsKeepTheOriginalSentenceAndSelectedToken() throws Exception {
        String document = "First. 🌊 A bank beside a bank.";
        JsonNode sentence = new ObjectMapper().readTree("""
                {"tokens":[
                  {"index":1,"characterOffsetBegin":7,"characterOffsetEnd":9},
                  {"index":2,"characterOffsetBegin":10,"characterOffsetEnd":11},
                  {"index":3,"characterOffsetBegin":12,"characterOffsetEnd":16},
                  {"index":4,"characterOffsetBegin":17,"characterOffsetEnd":23},
                  {"index":5,"characterOffsetBegin":24,"characterOffsetEnd":25},
                  {"index":6,"characterOffsetBegin":26,"characterOffsetEnd":30},
                  {"index":7,"characterOffsetBegin":30,"characterOffsetEnd":31}]}
                """);
        var context = OccurrenceContext.from(document, sentence, 6);
        assertEquals("🌊 A bank beside a <t>bank</t>.", context.markedText());
        assertEquals(19, context.begin());
        assertThrows(IllegalArgumentException.class, () -> OccurrenceContext.from(document, sentence, 8));
    }

    @Test
    void policyUsesRawRegressionScoresAndAbstainsOnWeakEvidence() {
        var high = candidate(4.2);
        assertSame(high, LexicalDisambiguation.select(List.of(high, candidate(3.0)), 2.5, 0.25));
        assertNull(LexicalDisambiguation.select(List.of(candidate(2.49)), 2.5, 0.25));
        assertNull(LexicalDisambiguation.select(List.of(candidate(3.0), candidate(2.9)), 2.5, 0.25));
        assertNull(LexicalDisambiguation.select(List.of(candidate(3.0), candidate(3.0)), 2.5, 0.0));
        var boundary = candidate(2.5);
        assertSame(boundary, LexicalDisambiguation.select(List.of(boundary, candidate(2.25)), 2.5, 0.25));
        assertNull(LexicalDisambiguation.select(List.of(), 2.5, 0.25));
    }

    private static LexicalDisambiguation.Candidate candidate(double score) {
        return new LexicalDisambiguation.Candidate(null, score, Set.of());
    }
}

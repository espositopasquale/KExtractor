package com.kextractor.triples.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class VerbNetFrameTest {

    @TempDir
    Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private VerbNetLexicon lexicon() throws Exception {
        Files.writeString(directory.resolve("fixture.xml"), """
            <!DOCTYPE VNCLASS SYSTEM "missing-local.dtd">
            <VNCLASS ID="transfer-1"><MEMBERS><MEMBER name="lend" wn="lend%2:40:00"/></MEMBERS><FRAMES>
            <FRAME><DESCRIPTION primary="NP V"/><SYNTAX><NP value="Agent"/><VERB/></SYNTAX></FRAME>
            <FRAME><DESCRIPTION primary="NP V NP"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Theme"/></SYNTAX></FRAME>
            <FRAME><DESCRIPTION primary="NP V NP PP"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Theme"/><PREP value="to"/><NP value="Recipient"/></SYNTAX></FRAME>
            <FRAME><DESCRIPTION primary="NP V NP NP"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Recipient"/><NP value="Theme"/></SYNTAX></FRAME>
            </FRAMES><SUBCLASSES><VNSUBCLASS ID="transfer-1-1"><MEMBERS><MEMBER name="donate" wn="donate%2:40:00"/></MEMBERS><FRAMES/></VNSUBCLASS></SUBCLASSES></VNCLASS>
            """);
        Files.writeString(directory.resolve("clause.xml"), """
            <VNCLASS ID="say-2"><MEMBERS><MEMBER name="report"/></MEMBERS><FRAMES>
            <FRAME><DESCRIPTION primary="NP V that S"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Topic"><SYNRESTRS><SYNRESTR Value="+" type="that_comp"/></SYNRESTRS></NP></SYNTAX></FRAME>
            </FRAMES></VNCLASS>
            """);
        Files.writeString(directory.resolve("particle.xml"), """
            <VNCLASS ID="lookup-3"><MEMBERS><MEMBER name="look"/></MEMBERS><FRAMES>
            <FRAME><DESCRIPTION primary="NP V NP"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Theme"/></SYNTAX></FRAME>
            <FRAME><DESCRIPTION primary="NP V NP up"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Theme"/><LEX value="up"/></SYNTAX></FRAME>
            </FRAMES></VNCLASS>
            """);
        Files.writeString(directory.resolve("control.xml"), """
            <VNCLASS ID="control-4"><MEMBERS><MEMBER name="ask"/></MEMBERS><FRAMES>
            <FRAME><DESCRIPTION primary="subject control"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Recipient"/><NP value="Topic"><SYNRESTRS><SYNRESTR Value="+" type="sc_to_inf"/></SYNRESTRS></NP></SYNTAX></FRAME>
            <FRAME><DESCRIPTION primary="object control"/><SYNTAX><NP value="Agent"/><VERB/><NP value="Recipient"/><NP value="Topic"><SYNRESTRS><SYNRESTR Value="+" type="oc_to_inf"/></SYNRESTRS></NP></SYNTAX></FRAME>
            </FRAMES></VNCLASS>
            """);
        return VerbNetLexicon.load(directory);
    }

    private static VerbNetLexicon.Argument arg(int token, String slot, String prep) {
        return new VerbNetLexicon.Argument(token, slot, prep);
    }

    @Test
    void adjunctPpLeavesCompleteTransitiveFrameAndCannotTurnItIntransitive() throws Exception {
        var matches = lexicon().match("lend", List.of(arg(1, "subject", ""), arg(3, "object1", ""), arg(6, "oblique", "during")));
        assertEquals(1, matches.size());
        assertEquals("theme", matches.get(0).roles().get(3));
        assertEquals(List.of(6), matches.get(0).unmatchedArguments());
        assertEquals(2, matches.get(0).matchedArgumentCount());
        assertEquals("NP[Agent] VERB NP[Theme]", matches.get(0).syntax());
    }

    @Test
    void fullFrameOutranksTransitiveFallbackWhenRecipientPrepMatches() throws Exception {
        var matches = lexicon().match("lend", List.of(arg(1, "subject", ""), arg(3, "object1", ""), arg(6, "oblique", "to"), arg(8, "oblique", "during")));
        assertEquals(1, matches.size());
        assertEquals("recipient", matches.get(0).roles().get(6));
        assertEquals(List.of(8), matches.get(0).unmatchedArguments());
        assertFalse(matches.get(0).roles().containsKey(8));
    }

    @Test
    void incorrectPrepositionCannotInventRecipientRole() throws Exception {
        var matches = lexicon().match("lend", List.of(arg(1, "subject", ""), arg(3, "object1", ""), arg(6, "oblique", "with")));
        assertEquals(1, matches.size());
        assertFalse(matches.get(0).roles().containsKey(6));
    }

    @Test
    void subclassPreservesInheritedFrameSourceAndOwnMembership() throws Exception {
        var matches = lexicon().match("donate", List.of(arg(1, "subject", ""), arg(3, "object1", "")));
        assertEquals("transfer-1-1", matches.get(0).classId());
        assertEquals("transfer-1#2", matches.get(0).frameId());
        assertTrue(lexicon().supportsSense("donate", "transfer-1-1", Set.of("donate%2:40:00::")));
    }

    @Test
    void finiteComplementConsumesTopicButIsNeverAnOrdinaryObject() throws Exception {
        var lexicon = lexicon();
        var matches = lexicon.match("report", List.of(arg(1, "subject", ""), arg(4, "ccomp", "that"), arg(7, "oblique", "on")));
        assertEquals("topic", matches.get(0).roles().get(4));
        assertTrue(matches.get(0).syntacticRestrictionsEvaluated());
        assertTrue(lexicon.match("report", List.of(arg(1, "subject", ""), arg(4, "object1", ""))).isEmpty());
        assertTrue(lexicon.match("lend", List.of(arg(1, "subject", ""), arg(4, "ccomp", "that"))).isEmpty());
    }

    @Test
    void particleMustMatchAndCannotBeSilentlyIgnored() throws Exception {
        var lexicon = lexicon();
        var good = lexicon.match("look", List.of(arg(1, "subject", ""), arg(3, "object1", ""), arg(4, "particle", "up")));
        assertEquals(1, good.size());
        assertEquals("lookup-3#2", good.get(0).frameId());
        assertTrue(lexicon.match("look", List.of(arg(1, "subject", ""), arg(3, "object1", ""), arg(4, "particle", "down"))).isEmpty());
    }

    @Test
    void dativeUsesDependenciesRatherThanSurfaceOrderAndSupportsAgentlessPassive() throws Exception {
        var tokens = new HashMap<Integer, JsonNode>();
        for (int i : List.of(1, 3, 5)) {
            tokens.put(i, JSON.readTree("{\"index\":" + i + ",\"lemma\":\"word\",\"pos\":\"NN\"}"));
        }
        var active = VerbNetRoles.arguments(2, false, List.of(edge("nsubj:xsubj", 2, 1), edge("obj", 2, 3), edge("iobj", 2, 5)), tokens);
        assertEquals("object1", active.stream().filter(a -> a.token() == 5).findFirst().orElseThrow().slot());
        assertEquals("object2", active.stream().filter(a -> a.token() == 3).findFirst().orElseThrow().slot());
        var passive = VerbNetRoles.arguments(2, true, List.of(edge("nsubj:pass", 2, 1), edge("obj", 2, 3)), tokens);
        assertEquals(-1, passive.stream().filter(a -> a.slot().equals("subject")).findFirst().orElseThrow().token());
        var matches = lexicon().match("lend", passive);
        assertEquals("recipient", matches.get(0).roles().get(1));
        assertEquals("theme", matches.get(0).roles().get(3));
    }

    @Test
    void infinitiveControlRequiresExplicitControllerEvidence() throws Exception {
        var tokens = new HashMap<Integer, JsonNode>();
        for (int i : List.of(1, 3, 5)) {
            tokens.put(i, JSON.readTree("{\"index\":" + i + ",\"lemma\":\"word\",\"pos\":\"VB\"}"));
        }
        tokens.put(6, JSON.readTree("{\"index\":6,\"lemma\":\"to\",\"pos\":\"TO\"}"));
        List<JsonNode> base = List.of(edge("nsubj", 2, 1), edge("obj", 2, 3), edge("xcomp", 2, 5), edge("mark", 5, 6));
        var lexicon = lexicon();
        assertTrue(lexicon.match("ask", VerbNetRoles.arguments(2, false, base, tokens)).isEmpty());
        var objectControl = new ArrayList<>(base);
        objectControl.add(edge("nsubj:xsubj", 5, 3));
        var matched = lexicon.match("ask", VerbNetRoles.arguments(2, false, objectControl, tokens));
        assertEquals(1, matched.size());
        assertEquals("object control", matched.get(0).description());
        var subjectControl = new ArrayList<>(base);
        subjectControl.add(edge("nsubj:xsubj", 5, 1));
        matched = lexicon.match("ask", VerbNetRoles.arguments(2, false, subjectControl, tokens));
        assertEquals(1, matched.size());
        assertEquals("subject control", matched.get(0).description());
    }

    @Test
    void passiveControlUsesActiveObjectForPromotedSubjectAndActiveSubjectForByAgent() throws Exception {
        var tokens = new HashMap<Integer, JsonNode>();
        for (int i : List.of(1, 3, 5)) {
            tokens.put(i, JSON.readTree("{\"index\":" + i + ",\"lemma\":\"word\",\"pos\":\"VB\"}"));
        }
        tokens.put(6, JSON.readTree("{\"index\":6,\"lemma\":\"to\",\"pos\":\"TO\"}"));
        var lexicon = lexicon();
        var promoted = List.of(edge("nsubj:pass", 2, 1), edge("xcomp", 2, 5), edge("mark", 5, 6), edge("nsubj:xsubj", 5, 1));
        var matches = lexicon.match("ask", VerbNetRoles.arguments(2, true, promoted, tokens));
        assertEquals(1, matches.size());
        assertEquals("object control", matches.get(0).description());
        var byAgent = List.of(edge("nsubj:pass", 2, 1), edge("obl:agent", 2, 3), edge("xcomp", 2, 5), edge("mark", 5, 6), edge("nsubj:xsubj", 5, 3));
        matches = lexicon.match("ask", VerbNetRoles.arguments(2, true, byAgent, tokens));
        assertEquals(1, matches.size());
        assertEquals("subject control", matches.get(0).description());
    }

    private static JsonNode edge(String dep, int governor, int dependent) throws Exception {
        return JSON.readTree("{\"dep\":\"" + dep + "\",\"governor\":" + governor + ",\"dependent\":" + dependent + "}");
    }
}

package com.kextractor.nlp.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyDiagnosticsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void preservesRootLexicalDataAndEnhancedArguments() throws Exception {
        var sentence = mapper.readTree("""
                {"tokens":[
                  {"index":1,"word":"Alice","lemma":"Alice","pos":"NNP"},
                  {"index":2,"word":"writes","lemma":"write","pos":"VBZ"},
                  {"index":3,"word":"books","lemma":"book","pos":"NNS"}],
                 "basicDependencies":[{"dep":"ROOT","governor":0,"dependent":2}],
                 "enhancedPlusPlusDependencies":[
                  {"dep":"ROOT","governor":0,"dependent":2,"dependentGloss":"writes"},
                  {"dep":"obj","governor":2,"dependent":3,"dependentGloss":"books"},
                  {"dep":"nsubj","governor":2,"dependent":1,"dependentGloss":"Alice"}]}
                """);
        var result = DependencyDiagnostics.rootVerbGraph(sentence);
        assertEquals("writes", result.get("rootVerb"));
        assertEquals("write", result.get("rootLemma"));
        assertEquals(2, result.get("rootIndex"));
        assertEquals(List.of(
                Map.of("relationship","nsubj","argumentWord","Alice","argumentIndex",1,"argumentPOS","NNP"),
                Map.of("relationship","obj","argumentWord","books","argumentIndex",3,"argumentPOS","NNS")),
                result.get("arguments"));
    }

    @Test void basicDependenciesRemainSupportedWithoutEnhancedAnnotations() throws Exception {
        var sentence = mapper.readTree("""
                {"tokens":[{"index":1,"word":"Run","lemma":"run","pos":"VB"}],
                 "basicDependencies":[{"dep":"ROOT","governor":0,"dependent":1,"dependentGloss":"Run"}]}
                """);
        var result = DependencyDiagnostics.rootVerbGraph(sentence);
        assertEquals("run", result.get("rootLemma"));
        assertEquals(List.of(), result.get("arguments"));
    }

    @Test void absentDependencyRootProducesNoDiagnosticGraph() throws Exception {
        assertNull(DependencyDiagnostics.rootVerbGraph(mapper.readTree("{\"tokens\":[],\"basicDependencies\":[]}")));
        assertNull(DependencyDiagnostics.rootVerbGraph(mapper.createObjectNode()));
    }
}

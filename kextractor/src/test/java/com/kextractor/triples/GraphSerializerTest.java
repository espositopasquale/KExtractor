package com.kextractor.triples;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.kextractor.utils.GraphFormat;

class GraphSerializerTest {

    private static final String KEX = "http://kextractor.com/entity/";

    private static Map<String, Object> graph(List<Map<String, Object>> nodes) {
        return Map.of("@context", Map.of("kex", KEX, "schema", "https://schema.org/"), "@graph", nodes);
    }

    @Test
    void owlDeclaresVocabularyWithoutPromotingScopedRolesToDomainRelations() {
        var input = graph(List.of(
                Map.of("@id", "kex:document", "@type", "schema:DigitalDocument", "schema:text", "Alice did not leave."),
                Map.of("@id", "kex:event", "@type", "kex:SemanticFrame", "kex:predicate", "leave",
                        "kex:negated", true, "kex:agent", Map.of("@id", "kex:alice")),
                Map.of("@id", "kex:alice", "@type", "schema:Person", "schema:name", "Alice")));
        String serialized = GraphSerializer.serialize(input, GraphFormat.OWL);
        var model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)), Lang.RDFXML);
            assertTrue(model.contains(model.createResource(KEX + "document_ontology"), RDF.type, OWL.Ontology));
            assertTrue(model.contains(model.createResource(KEX + "SemanticFrame"), RDF.type, OWL.Class));
            assertTrue(model.contains(model.createResource(KEX + "predicate"), RDF.type, OWL.DatatypeProperty));
            assertTrue(model.contains(model.createResource(KEX + "negated"), RDF.type, OWL.DatatypeProperty));
            assertTrue(model.contains(model.createResource(KEX + "agent"), RDF.type, OWL.ObjectProperty));
            assertTrue(model.contains(model.createResource(KEX + "event"), model.createProperty(KEX + "agent"), model.createResource(KEX + "alice")));
            assertFalse(model.contains(model.createResource(KEX + "alice"), model.createProperty(KEX + "leave")));
        } finally {
            model.close();
        }
        assertFalse(input.toString().contains("owl:Ontology"));
    }

    @Test
    void refusesPropertyThatWouldBeBothObjectAndDataProperty() {
        var input = graph(List.of(
                Map.of("@id", "kex:a", "kex:value", "label"),
                Map.of("@id", "kex:b", "kex:value", Map.of("@id", "kex:a"))));
        var error = assertThrows(IllegalArgumentException.class, () -> GraphSerializer.serialize(input, GraphFormat.OWL));
        assertTrue(error.getMessage().contains("object and data properties"));
        assertDoesNotThrow(() -> GraphSerializer.serialize(input, GraphFormat.RDFXML));
    }

    @Test
    void refusesClassPropertyIriCollision() {
        var input = graph(List.of(Map.of("@id", "kex:a", "@type", "kex:Role", "kex:Role", "agent")));
        var error = assertThrows(IllegalArgumentException.class, () -> GraphSerializer.serialize(input, GraphFormat.OWL));
        assertTrue(error.getMessage().contains("class and property IRIs"));
    }
}

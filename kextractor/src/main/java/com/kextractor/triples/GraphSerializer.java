package com.kextractor.triples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.utils.GraphFormat;

public class GraphSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GraphSerializer() {
    }

    public static String serialize(Map<String, Object> jsonLdGraph, GraphFormat format) {
        try {
            String jsonLdString = MAPPER.writeValueAsString(jsonLdGraph);
            Model model = parseJsonLd(jsonLdString);
            try {
                if (format == GraphFormat.OWL) {
                    declareOntology(model);
                }
                return writeModel(model, toJenaLang(format));
            } finally {
                model.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize graph to " + format, e);
        }
    }

    private static void declareOntology(Model model) {
        Set<String> classes = new LinkedHashSet<>();
        Set<String> objectProperties = new LinkedHashSet<>();
        Set<String> dataProperties = new LinkedHashSet<>();
        String ontologyId = "http://kextractor.com/ontology/semantic-graph";
        for (var statement : model.listStatements().toList()) {
            String predicate = statement.getPredicate().getURI();
            if (statement.getPredicate().equals(RDF.type)) {
                if (statement.getObject().isURIResource()) {
                    String type = statement.getResource().getURI();
                    classes.add(type);
                    if (type.equals("https://schema.org/DigitalDocument") && statement.getSubject().isURIResource()) {
                        ontologyId = statement.getSubject().getURI() + "_ontology";
                    }
                }
            } else if (statement.getObject().isLiteral()) {
                dataProperties.add(predicate); 
            }else {
                objectProperties.add(predicate);
            }
        }
        Set<String> mixed = new LinkedHashSet<>(objectProperties);
        mixed.retainAll(dataProperties);
        if (!mixed.isEmpty()) {
            throw new IllegalArgumentException(
                    "OWL export requires separate object and data properties: " + mixed);
        }
        Set<String> classProperties = new LinkedHashSet<>(classes);
        Set<String> properties = new LinkedHashSet<>(objectProperties);
        properties.addAll(dataProperties);
        classProperties.retainAll(properties);
        if (!classProperties.isEmpty()) {
            throw new IllegalArgumentException(
                    "OWL export requires separate class and property IRIs: " + classProperties);
        }
        model.setNsPrefix("owl", OWL.NS);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);
        model.setNsPrefix("kex", "http://kextractor.com/entity/");
        var ontology = model.createResource(ontologyId);
        ontology.addProperty(RDF.type, OWL.Ontology);
        ontology.addProperty(RDFS.label, "KExtractor sentence structure and event descriptions");
        ontology.addProperty(RDFS.comment,
                "Event descriptions preserve source scope, negation and modality; role links do not assert that events occurred.");
        for (String type : classes) {
            model.createResource(type).addProperty(RDF.type, OWL.Class);
        }
        for (String property : objectProperties) {
            model.createResource(property).addProperty(RDF.type, OWL.ObjectProperty);
        }
        for (String property : dataProperties) {
            model.createResource(property).addProperty(RDF.type, OWL.DatatypeProperty);
        }
    }

    private static Model parseJsonLd(String jsonLdString) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = new ByteArrayInputStream(jsonLdString.getBytes(StandardCharsets.UTF_8))) {
            RDFDataMgr.read(model, in, Lang.JSONLD);
        } catch (IOException e) {
            model.close();
            throw new IllegalStateException("Failed to parse generated JSON-LD graph", e);
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
        return model;
    }

    private static String writeModel(Model model, Lang lang) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, lang);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Lang toJenaLang(GraphFormat format) {
        switch (format) {
            case JSONLD:
                return Lang.JSONLD;
            case TURTLE:
                return Lang.TURTLE;
            case NQUADS:
                return Lang.NQUADS;
            case RDFXML:
            case OWL:
                return Lang.RDFXML;
            default:
                throw new IllegalArgumentException("Unhandled format: " + format);
        }
    }
}

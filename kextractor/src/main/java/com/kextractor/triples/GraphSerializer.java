package com.kextractor.triples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

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
            return writeModel(model, toJenaLang(format));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize graph to " + format, e);
        }
    }

    private static Model parseJsonLd(String jsonLdString) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = new ByteArrayInputStream(jsonLdString.getBytes(StandardCharsets.UTF_8))) {
            RDFDataMgr.read(model, in, Lang.JSONLD);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse generated JSON-LD graph", e);
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
                return Lang.RDFXML;
            default:
                throw new IllegalArgumentException("Unhandled format: " + format);
        }
    }
}
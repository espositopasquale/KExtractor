package com.kextractor.utils;

import java.util.Locale;

public enum GraphFormat {
    JSONLD,
    TURTLE,
    NQUADS,
    RDFXML,
    OWL;

    public static GraphFormat fromString(String value) {
        if (value == null) {
            return JSONLD;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        switch (normalized) {
            case "jsonld":
            case "json":
                return JSONLD;
            case "turtle":
            case "ttl":
                return TURTLE;
            case "nquads":
            case "nq":
                return NQUADS;
            case "rdfxml":
            case "rdf":
            case "xml":
                return RDFXML;
            case "owl":
                return OWL;
            default:
                throw new IllegalArgumentException("Unsupported graph format: " + value);
        }
    }
}

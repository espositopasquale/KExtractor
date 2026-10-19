package com.kextractor.nlp.disambiguation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class WordNetResources {

    private static Path packaged;

    static synchronized String directory() {
        var loader = WordNetResources.class.getClassLoader();
        var marker = loader.getResource("dict/data.verb");
        if (marker == null) {
            return null;
        }
        try {
            if (marker.getProtocol().equals("file")) {
                return Path.of(marker.toURI()).getParent().toString();
            }
            if (packaged != null) {
                return packaged.toString();
            }
            Path temp = Files.createTempDirectory("kextractor-wordnet-");
            temp.toFile().deleteOnExit();
            for (String file : List.of("data.noun", "data.verb", "data.adj", "data.adv",
                    "index.noun", "index.verb", "index.adj", "index.adv",
                    "noun.exc", "verb.exc", "adj.exc", "adv.exc", "index.sense", "lexnames",
                    "cntlist", "cntlist.rev", "sentidx.vrb", "sents.vrb", "frames.vrb")) {
                try (var input = loader.getResourceAsStream("dict/" + file)) {
                    if (input != null) {
                        Path target = temp.resolve(file);
                        target.toFile().deleteOnExit();
                        Files.copy(input, target);
                    } else if (file.startsWith("data.") || file.startsWith("index.") && !file.equals("index.sense")) {
                        throw new IOException("Incomplete bundled WordNet: missing " + file);
                    }
                }
            }
            packaged = temp;
            return temp.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read bundled WordNet dictionary", e);
        }
    }

    private WordNetResources() {
    }
}

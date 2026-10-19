package com.kextractor.nlp.disambiguation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IVersion;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;

public final class WordNetRepository implements AutoCloseable {

    public record Sense(ISynset synset, Set<String> senseKeys) {

        public Sense {
            senseKeys = Set.copyOf(senseKeys);
        }
    }
    private final IDictionary dictionary;
    private final WordnetStemmer stemmer;

    public static WordNetRepository configured() throws IOException {
        String path = System.getProperty("kextractor.wordnet.dir", System.getenv("KEX_WORDNET_DIR"));
        if (path == null && Files.isRegularFile(Path.of("dict/data.verb"))) {
            path = "dict";
        }
        if (path == null) {
            path = WordNetResources.directory();
        }
        if (path == null) {
            throw new IOException("WordNet dictionary missing: configure KEX_WORDNET_DIR or bundle dict/");
        }
        return new WordNetRepository(Path.of(path));
    }

    public WordNetRepository(Path directory) throws IOException {
        if (!Files.isRegularFile(directory.resolve("data.verb"))) {
            throw new IOException("Missing WordNet dictionary: " + directory);
        }
        dictionary = new Dictionary(directory.toUri().toURL());
        try {
            if (!dictionary.open()) {
                throw new IOException("Cannot open WordNet: " + directory);
            }
        } catch (IOException | RuntimeException e) {
            dictionary.close();
            throw e;
        }
        stemmer = new WordnetStemmer(dictionary);
    }

    public List<Sense> candidates(String lemma, String pennPos) {
        POS pos = pos(pennPos);
        if (pos == null || lemma == null || lemma.isBlank()) {
            return List.of();
        }
        String normalized = lemma.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        Set<String> forms = new TreeSet<>();
        forms.add(normalized);
        forms.addAll(stemmer.findStems(normalized, pos));
        Map<ISynsetID, ISynset> synsets = new LinkedHashMap<>();
        Map<ISynsetID, Set<String>> keys = new HashMap<>();
        for (String form : forms) {
            IIndexWord index = dictionary.getIndexWord(form, pos);
            if (index == null) {
                continue;
            }
            for (IWordID id : index.getWordIDs()) {
                IWord word = dictionary.getWord(id);
                ISynset synset = word.getSynset();
                synsets.put(synset.getID(), synset);
                keys.computeIfAbsent(synset.getID(), k -> new TreeSet<>()).add(word.getSenseKey().toString());
            }
        }
        return synsets.values().stream().map(s -> new Sense(s, keys.get(s.getID())))
                .sorted(Comparator.comparing(s -> s.synset().getID().toString())).toList();
    }

    public String version() {
        IVersion version = dictionary.getVersion();
        return version == null ? "unspecified" : version.getMajorVersion() + "." + version.getMinorVersion();
    }

    public static POS pos(String tag) {
        if (tag == null) {
            return null;
        }
        return tag.startsWith("NN") ? POS.NOUN : tag.startsWith("VB") ? POS.VERB
                : tag.startsWith("JJ") ? POS.ADJECTIVE : tag.startsWith("RB") ? POS.ADVERB : null;
    }

    @Override
    public void close() {
        dictionary.close();
    }
}

package com.kextractor.nlp.coreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kextractor.utils.Replacement;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;

public class CorefResolver {

    private static final Set<String> PLURAL_PRONOUNS = Set.of(
            "they", "them", "their", "theirs", "themselves");

    public static String resolveCoreferences(CoreDocument doc, boolean pronounsOnly) {

        if (doc == null || doc.corefChains() == null) {
            return doc == null ? null : doc.text();
        }

        List<Replacement> replacements = new ArrayList<>();
        Map<Integer, CorefChain> chains = doc.corefChains();

        for (CorefChain chain : chains.values()) {

            CorefChain.CorefMention rep = chain.getRepresentativeMention();

            if (rep == null || rep.mentionSpan == null || rep.mentionSpan.isBlank()) {
                continue;
            }

            String repText = rep.mentionSpan.trim();

            for (CorefChain.CorefMention m : chain.getMentionsInTextualOrder()) {

                if (m.mentionID == rep.mentionID) {
                    continue;
                }

                if (m.mentionSpan.equalsIgnoreCase(repText)) {
                    continue;
                }

                
                if (m.mentionType == Dictionaries.MentionType.PROPER) {
                    continue;
                }

                
                if (pronounsOnly &&
                        m.mentionType != Dictionaries.MentionType.PRONOMINAL) {
                    continue;
                }

                if (PLURAL_PRONOUNS.contains(m.mentionSpan.toLowerCase())) {
                    continue;
                }

                if (m.sentNum < 1 || m.sentNum > doc.sentences().size()) {
                    continue;
                }

                List<CoreLabel> tokens = doc.sentences()
                        .get(m.sentNum - 1)
                        .tokens();

                if (m.startIndex < 1 ||
                        m.endIndex - 2 >= tokens.size() ||
                        m.startIndex - 1 > m.endIndex - 2) {
                    continue;
                }

                int beginChar = tokens.get(m.startIndex - 1).beginPosition();
                int endChar = tokens.get(m.endIndex - 2).endPosition();

                replacements.add(new Replacement(beginChar, endChar, repText));
            }
        }

        replacements.sort(
                Comparator.comparingInt((Replacement r) -> r.start).reversed());

        StringBuilder sb = new StringBuilder(doc.text());

        int previousStart = Integer.MAX_VALUE;

        for (Replacement r : replacements) {

            if (r.end > previousStart) {
                continue;
            }

            sb.replace(r.start, r.end, r.text);
            previousStart = r.start;
        }

        return sb.toString();
    }
}
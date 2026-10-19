package com.kextractor.nlp.pipeline.nonserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.nlp.coreference.CorefResolver;
import com.kextractor.nlp.pipeline.DependencyDiagnostics;
import com.kextractor.triples.GraphSerializer;
import com.kextractor.triples.semantic.SemanticGraphBuilder;
import com.kextractor.utils.GraphFormat;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreEntityMention;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.JSONOutputter;
import edu.stanford.nlp.util.CoreMap;

public class Pipeline {

    public static HashMap<String, Object> pipelineMain(String text, String graphFormat, boolean fullAnalysis)
            throws Exception {
        HashMap<String, Object> result = new HashMap<>();
        result.put("originalText", text);

        CoreDocument doc = new CoreDocument(text);
        Helper.getPipeline().annotate(doc);
        JsonNode originalAnnotation = new ObjectMapper().readTree(JSONOutputter.jsonPrint(doc.annotation()));

        // 1. Extract Tokens
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> tokens = new ArrayList<>();
            for (CoreLabel token : doc.tokens()) {
                HashMap<String, Object> t = new HashMap<>();
                t.put("word", token.word());
                t.put("lemma", token.lemma());
                t.put("pos", token.tag());
                t.put("ner", token.ner());
                t.put("begin", token.beginPosition());
                t.put("end", token.endPosition());
                tokens.add(t);
            }
            result.put("tokens", tokens);
        }

        // 2. Extract Entities 
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> entities = new ArrayList<>();
            for (CoreEntityMention em : doc.entityMentions()) {
                HashMap<String, Object> e = new HashMap<>();
                e.put("text", em.text());
                e.put("type", em.entityType());
                entities.add(e);
            }
            result.put("entities", entities);
        }

        // 3. Extract Sentences
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> sentences = new ArrayList<>();
            int sid = 1;
            for (CoreSentence sentence : doc.sentences()) {
                HashMap<String, Object> s = new HashMap<>();
                s.put("id", sid);
                s.put("text", sentence.text());
                s.put("rootVerbKnowledgeGraph", DependencyDiagnostics.rootVerbGraph(originalAnnotation.path("sentences").get(sid - 1)));
                sid++;
                sentences.add(s);
            }
            result.put("sentences", sentences);
        }

        // 4. Extract Coreferences
        if (fullAnalysis) {
            ArrayList<HashMap<String, Object>> corefs = new ArrayList<>();
            Map<Integer, CorefChain> chains = doc.corefChains();
            for (CorefChain chain : chains.values()) {
                HashMap<String, Object> c = new HashMap<>();
                c.put("id", chain.getChainID());
                c.put("representative", chain.getRepresentativeMention().mentionSpan);
                ArrayList<HashMap<String, Object>> mentions = new ArrayList<>();
                for (CorefChain.CorefMention m : chain.getMentionsInTextualOrder()) {
                    HashMap<String, Object> mm = new HashMap<>();
                    mm.put("text", m.mentionSpan);
                    mm.put("sentence", m.sentNum);
                    mm.put("startIndex", m.startIndex);
                    mm.put("endIndex", m.endIndex);
                    mentions.add(mm);
                }
                c.put("mentions", mentions);
                corefs.add(c);
            }
            result.put("coreferences", corefs);
        }

        // Optional OpenIE diagnostics; the final graph always uses original-text annotations.
        if (fullAnalysis) {
            // 5. Resolve coreferences
            String resolvedText = CorefResolver.resolveCoreferences(doc, false);
            result.put("resolvedText", resolvedText);

            Annotation legacyDoc;
            if (resolvedText.equals(text)) {
                legacyDoc = doc.annotation();
            } else {
                legacyDoc = new Annotation(resolvedText);
                Helper.getPipeline().annotate(legacyDoc);
            }

            // 6. Extract diagnostic OpenIE triples
            ArrayList<HashMap<String, Object>> relations = new ArrayList<>();
            for (CoreMap sentence : legacyDoc.get(CoreAnnotations.SentencesAnnotation.class)) {
                Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
                if (triples != null) {
                    for (RelationTriple triple : triples) {
                        HashMap<String, Object> r = new HashMap<>();
                        r.put("confidence", triple.confidence);
                        r.put("subject", triple.subjectGloss());
                        r.put("relation", triple.relationGloss());
                        r.put("object", triple.objectGloss());
                        relations.add(r);
                    }
                }
            }
            result.put("triples", relations);

        }

        Map<String, Object> semanticGraph = SemanticGraphBuilder.build(text, originalAnnotation);

        GraphFormat format = GraphFormat.fromString(graphFormat);
        if (format == GraphFormat.JSONLD) {
            result.put("rdfKnowledgeGraph", semanticGraph);
        } else {
            String serialized = GraphSerializer.serialize(semanticGraph, format);
            result.put("rdfKnowledgeGraph", serialized);
        }

        return result;
    }
}

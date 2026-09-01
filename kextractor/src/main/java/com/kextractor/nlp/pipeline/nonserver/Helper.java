package com.kextractor.nlp.pipeline.nonserver;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;

public class Helper {

    public static StanfordCoreNLP pipeline = null;

    public static StanfordCoreNLP getPipeline() {
        if (pipeline == null) {
            pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties(
                
                    "annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,natlog,openie",
                    "coref.algorithm", "statistical",
                    "tokenize.language", "en",
                    "tokenize.options", "splitHyphenated=false,americanize=true",
                                        
                    "ner.applyFineGrained", "true",            
                    "ner.combinationMode", "HIGH_RECALL",      
                    "ner.applyNumericClassifiers", "true",     
                    "ner.useSUTime", "true",        
                    
                    "ner.sutime.markTimeRanges", "true",           
                    "ner.sutime.includeRange", "true"));
        }

        return pipeline;
    }

}
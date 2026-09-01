package com.kextractor.nlp.pipeline.server;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CoreNLPClient {

        private static final String PROPERTIES_JSON = "{" +
                        "\"outputFormat\":\"json\"," +
                        "\"annotators\":\"tokenize,ssplit,pos,lemma,ner,depparse,coref,natlog,openie\"," +
                        "\"coref.algorithm\":\"statistical\"," +
                        "\"tokenize.language\":\"en\"," +
                        "\"tokenize.options\":\"splitHyphenated=false,americanize=true\"," +
                        "\"ner.applyFineGrained\":\"true\"," +
                        "\"ner.combinationMode\":\"HIGH_RECALL\"," +
                        "\"ner.applyNumericClassifiers\":\"true\"," +
                        "\"ner.useSUTime\":\"true\"," +
                        "\"ner.sutime.markTimeRanges\":\"true\"," +
                        "\"ner.sutime.includeRange\":\"true\"" +
                        "}";

        public static String analyzeText(String text) throws Exception {
                String encodedProps = URLEncoder.encode(PROPERTIES_JSON, StandardCharsets.UTF_8);
                String finalUrl = "http://localhost:9000/?properties=" + encodedProps;

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(finalUrl))
                                .POST(HttpRequest.BodyPublishers.ofString(text))
                                .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                        System.err.println("\u001B[31mCORENLP SERVER INTERNAL ERROR:\u001B[0m");
                        System.err.println(response.body());
                        throw new RuntimeException(
                                        "Extraction failed. The CoreNLP server responded with HTTP status "
                                                        + response.statusCode());
                }

                return response.body();
        }
}
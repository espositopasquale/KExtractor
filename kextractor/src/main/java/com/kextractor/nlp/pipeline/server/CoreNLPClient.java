package com.kextractor.nlp.pipeline.server;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CoreNLPClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();

    private static final String PROPERTIES_JSON = "{"
            + "\"outputFormat\":\"json\","
            + "\"annotators\":\"tokenize,ssplit,pos,lemma,ner,depparse,coref,natlog,openie\","
            + "\"coref.algorithm\":\"statistical\","
            + "\"depparse.extradependencies\":\"MAXIMAL\","
            + "\"tokenize.language\":\"en\","
            + "\"tokenize.options\":\"splitHyphenated=false,americanize=true\","
            + "\"ner.applyFineGrained\":\"true\","
            + "\"ner.combinationMode\":\"HIGH_RECALL\","
            + "\"ner.applyNumericClassifiers\":\"true\","
            + "\"ner.useSUTime\":\"true\","
            + "\"ner.sutime.markTimeRanges\":\"true\","
            + "\"ner.sutime.includeRange\":\"true\""
            + "}";

    public static String analyzeText(String text) throws Exception {
        String encodedProps = URLEncoder.encode(PROPERTIES_JSON, StandardCharsets.UTF_8);
        String finalUrl = "http://localhost:9000/?properties=" + encodedProps;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .timeout(java.time.Duration.ofMinutes(25))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(text))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

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

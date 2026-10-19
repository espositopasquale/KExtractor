/*
 * KExtractor - Semantic Knowledge Extraction CLI
 * 2026 Pasquale Esposito
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kextractor.app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.banner.Banner;
import com.kextractor.nlp.pipeline.nonserver.Pipeline;
import com.kextractor.nlp.pipeline.server.ServerPipeline;
import com.kextractor.utils.GraphFormat;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            logger.error("Extraction failed: {}", e.getMessage());
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        if (options.help()) {
            System.out.println("Usage: java -jar kextractor.jar [--server] [--full-analysis] [--graph-only] [--format jsonld|ttl|nquads|rdf|owl] <text|file.txt> [output_file]");
            return;
        }

        Banner.printBanner();
        logger.info("KExtractor session started");

        boolean useServer = options.server();
        boolean fullAnalysis = options.fullAnalysis();
        boolean graphOnly = options.graphOnly();
        String text = options.input();
        String outputPath = options.output();
        String graphFormat = options.format();
        String inputArg = options.input();
        if (inputArg.endsWith(".txt")) {
            text = Files.readString(Paths.get(inputArg));
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Input text must not be empty");
        }

        logger.info("Output will be written to: {}", outputPath);
        logger.info("Requested graph format: {}", graphFormat);

        GraphFormat requestedFormat = GraphFormat.fromString(graphFormat);

        HashMap<String, Object> result;
        long startTime = System.currentTimeMillis();

        if (useServer) {
            System.out.println("""
                               \u001b[36mRouting extraction to CoreNLP Server...\u001b[0m
                               """);
            result = ServerPipeline.pipelineServerMain(text, graphFormat, fullAnalysis);
        } else {
            Banner.printWarmupMessage();
            result = Pipeline.pipelineMain(text, graphFormat, fullAnalysis);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Pipeline execution completed in {} ms", elapsed);
        System.out.println("\u001B[32m" + "Extraction complete in " + (elapsed / 1000.0) + " seconds." + "\u001B[0m\n");

        {
            if (requestedFormat == GraphFormat.JSONLD) {
                ObjectMapper mapper = new ObjectMapper();
                Object output = graphOnly ? result.get("rdfKnowledgeGraph") : result;
                mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), output);
            } else {
                Object serializedGraph = result.get("rdfKnowledgeGraph");
                if (!(serializedGraph instanceof String)) {
                    throw new IllegalStateException("Pipeline did not return serialized graph data");
                }
                Files.writeString(Paths.get(outputPath), (String) serializedGraph);
            }
            logger.info("Knowledge graph serialized successfully to {}", outputPath);
            System.out.println("Processing complete! Data successfully exported to: " + outputPath);
        }
    }
}

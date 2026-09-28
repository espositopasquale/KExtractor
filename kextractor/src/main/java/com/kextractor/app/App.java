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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kextractor.banner.Banner;
import com.kextractor.nlp.pipeline.nonserver.Pipeline;
import com.kextractor.nlp.pipeline.server.ServerPipeline;
import com.kextractor.utils.GraphFormat;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private static final String DEFAULT_GRAPH_FORMAT = "jsonld";

    public static void main(String[] args) throws Exception, IOException {

        Banner.printBanner();
        logger.info("KExtractor session started");

        boolean useServer = false;
        boolean fullAnalysis = false;
        boolean graphOnly = false;
        String text = "Project Beta Cloud Migration was executed by Neo, the One, with assistance from Trinity, a skilled operator of the Matrix. The mission targeted a legacy system hidden deep within the Matrix and successfully migrated its core infrastructure into a secure digital environment. Before the operation began, Morpheus reviewed the migration plan and assessed the security protocols to ensure the mission was ready for execution. The operation commenced at 02:00 AM and concluded at 04:00 AM on March 14, 2024. The total estimated cost of the operation was 12,000 EUR. Before joining the crew aboard the Nebuchadnezzar, Neo had been trapped inside the Matrix, while Trinity had already established herself as one of its most capable operators.";
        String outputPath = "kextractor-output.json";
        String graphFormat = DEFAULT_GRAPH_FORMAT;
        

        List<String> cleanArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--server")) {
                useServer = true;
            } else if (arg.toLowerCase().startsWith("--format=")) {
                graphFormat = arg.substring("--format=".length());
            } else if (arg.equalsIgnoreCase("--full-analysis")) {
                fullAnalysis = true;
            } else if (arg.equalsIgnoreCase("--graph-only")) {
                graphOnly = true;
            } else {
                cleanArgs.add(arg);
            }
        }

        if (!cleanArgs.isEmpty()) {
            String inputArg = cleanArgs.get(0);
            if (inputArg.endsWith(".txt")) {
                System.out.println("Reading text from file: " + inputArg);
                logger.debug("Loading input text from file: {}", inputArg);
                text = new String(Files.readAllBytes(Paths.get(inputArg)));
            } else {
                text = inputArg;
                logger.debug("Using direct string input ({} characters)", text.length());
            }
        }

        if (cleanArgs.size() > 1) {
            outputPath = cleanArgs.get(1);
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

        try {
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
        } catch (IOException e) {
            logger.error("Failed to write JSON output to {}", outputPath, e);
            System.err.println("\u001B[31mError saving the JSON file:\u001B[0m");
            e.printStackTrace();
        }
    }
}
package com.kextractor.nlp.disambiguation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

public final class GradedWsdModel implements AutoCloseable {

    public static final String MODEL_ID = "ChangeIsKey/graded-wsd";
    private final Path directory;
    private final GradedWsdTokenizer tokenizer;
    private final OrtSession session;
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final boolean tokenTypeIds;

    public static Path configuredDirectory() throws IOException {
        String configured = System.getProperty("kextractor.wsd.model.dir", System.getenv("KEX_WSD_MODEL_DIR"));
        if (configured != null) {
            return requireDirectory(Path.of(configured));
        }
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (String name : List.of("models/graded-wsd", "models")) {
                Path candidate = base.resolve(name);
                if (Files.isRegularFile(candidate.resolve("model.onnx")) && Files.isRegularFile(candidate.resolve("tokenizer.json"))) {
                    return requireDirectory(candidate);
                }
            }
        }
        throw new IOException("graded-wsd missing: set KEX_WSD_MODEL_DIR to the model/tokenizer directory");
    }

    private static Path requireDirectory(Path path) throws IOException {
        for (String file : List.of("model.onnx", "tokenizer.json", "config.json")) {
            if (!Files.isRegularFile(path.resolve(file))) {
                throw new IOException("Missing graded-wsd asset: " + path.resolve(file));
            }
        }
        return path.toAbsolutePath().normalize();
    }

    public GradedWsdModel(Path path) throws Exception {
        directory = requireDirectory(path);
        JsonNode config = new ObjectMapper().readTree(directory.resolve("config.json").toFile());
        if (!config.path("model_type").asText().equals("roberta") || !config.path("problem_type").asText().equals("regression")) {
            throw new IOException("Expected graded-wsd RoBERTa regression config");
        }
        tokenizer = new GradedWsdTokenizer(directory.resolve("tokenizer.json"));
        OrtSession opened;
        try (var options = new OrtSession.SessionOptions()) {
            int threads = Integer.parseInt(System.getProperty("kextractor.wsd.threads", "2"));
            if (threads < 1) {
                throw new IllegalArgumentException("wsd.threads must be positive");
            }
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            options.setCPUArenaAllocator(false);
            options.setMemoryPatternOptimization(false);
            opened = environment.createSession(directory.resolve("model.onnx").toString(), options);
        }
        try {
            if (!opened.getInputNames().containsAll(Set.of("input_ids", "attention_mask"))
                    || !Set.of("input_ids", "attention_mask", "token_type_ids").containsAll(opened.getInputNames())
                    || !opened.getOutputNames().contains("logits")) {
                throw new IOException("Expected graded-wsd INT64 token inputs and regression logits output");
            }
            for (NodeInfo input : opened.getInputInfo().values()) {
                if (!(input.getInfo() instanceof TensorInfo info) || info.type != OnnxJavaType.INT64 || info.getShape().length != 2) {
                    throw new IOException("Unsupported graded-wsd input tensor: " + input);
                }
            }
            NodeInfo output = opened.getOutputInfo().get("logits");
            if (!(output.getInfo() instanceof TensorInfo info) || info.type != OnnxJavaType.FLOAT
                    || info.getShape().length != 2 || info.getShape()[1] != 1) {
                throw new IOException("Expected one float regression logit per candidate: " + output);
            }
        } catch (Exception e) {
            opened.close();
            throw e;
        }
        session = opened;
        tokenTypeIds = session.getInputNames().contains("token_type_ids");
    }

    public double score(OccurrenceContext context, String definition) throws Exception {
        long[] ids = tokenizer.encode(context.markedText(), definition);
        long[] mask = new long[ids.length];
        Arrays.fill(mask, 1);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][]{ids}));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, new long[][]{mask}));
            if (tokenTypeIds) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, new long[][]{new long[ids.length]}));
            }
            try (var result = session.run(inputs, Set.of("logits"))) {
                Object raw = result.get("logits").orElseThrow().getValue();
                if (!(raw instanceof float[][] logits) || logits.length != 1 || logits[0].length != 1 || !Float.isFinite(logits[0][0])) {
                    throw new IOException("Invalid graded-wsd regression output");
                }
                return logits[0][0];
            }
        } finally {
            for (var tensor : inputs.values()) {
                tensor.close();
        
            }}
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}

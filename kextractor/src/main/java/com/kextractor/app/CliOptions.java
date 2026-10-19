package com.kextractor.app;

import java.util.ArrayList;
import java.util.List;

import com.kextractor.utils.GraphFormat;

record CliOptions(boolean server, boolean fullAnalysis, boolean graphOnly,
        boolean help, boolean version, boolean verbose, CliConsole.ColorMode color,
        String format, String input, String output) {

    static CliConsole.ColorMode colorMode(String[] args) {
        CliConsole.ColorMode mode = CliConsole.ColorMode.AUTO;
        for (int i = 0; i < args.length && !args[i].equals("--"); i++) {
            if (args[i].equals("--no-color")) {
                mode = CliConsole.ColorMode.NEVER;
            } else if (args[i].startsWith("--color=")) {
                mode = CliConsole.ColorMode.parse(args[i].substring(8));
            } else if (args[i].equals("--color")) {
                if (++i == args.length) {
                    throw new IllegalArgumentException("Missing value for --color");
                }
                mode = CliConsole.ColorMode.parse(args[i]);
            }
        }
        return mode;
    }

    static boolean verboseRequested(String[] args) {
        for (String arg : args) {
            if (arg.equals("--")) {
                break;

            }
            if (arg.equals("--verbose")) {
                return true;

            }
        }
        return false;
    }

    static CliOptions parse(String[] args) {
        boolean server = false, full = false, graph = false, help = false, version = false, verbose = false;
        CliConsole.ColorMode color = colorMode(args);
        String format = "jsonld";
        List<String> positional = new ArrayList<>();
        boolean literal = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (literal) {
                positional.add(arg);
                continue;
            }
            switch (arg) {
                case "--" ->
                    literal = true;
                case "--help", "-h" ->
                    help = true;
                case "--version", "-V" ->
                    version = true;
                case "--verbose" ->
                    verbose = true;
                case "--no-color" -> {
                }
                case "--color" ->
                    i++;
                case "--server" ->
                    server = true;
                case "--full-analysis" ->
                    full = true;
                case "--graph-only" ->
                    graph = true;
                case "--format" -> {
                    if (++i >= args.length || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for --format");
                    }
                    format = args[i];
                }
                default -> {
                    if (arg.startsWith("--color=")) {
                    } else if (arg.startsWith("--format=")) {
                        format = arg.substring(9);
                    } else if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    } else {
                        positional.add(arg);
                    }
                }
            }
        }
        if (help || version) {
            return new CliOptions(server, full, graph, help, version, verbose, color, format, "", "");
        }
        GraphFormat parsed = GraphFormat.fromString(format);
        if (positional.isEmpty() || positional.size() > 2 || positional.get(0).isBlank()) {
            throw new IllegalArgumentException("Provide text or a .txt file and optionally an output path; use --help");
        }
        String extension = switch (parsed) {
            case JSONLD ->
                "json";
            case TURTLE ->
                "ttl";
            case NQUADS ->
                "nq";
            case RDFXML ->
                "rdf";
            case OWL ->
                "owl";
        };
        String output = positional.size() == 2 ? positional.get(1) : "kextractor-output." + extension;
        if (output.isBlank()) {
            throw new IllegalArgumentException("Output path must not be empty");
        }
        return new CliOptions(server, full, graph, false, false, verbose, color, format, positional.get(0), output);
    }
}

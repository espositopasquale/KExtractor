package com.kextractor.app;

import java.io.PrintStream;
import java.util.Locale;

public final class CliConsole {

    public enum ColorMode {
        AUTO, ALWAYS, NEVER;

        public static ColorMode parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid color mode '" + value + "'; choose auto, always or never");
            }
        }
    }
    private final boolean color;
    private final PrintStream out;
    private final PrintStream err;

    private CliConsole(ColorMode mode) {
        color = mode == ColorMode.ALWAYS || mode == ColorMode.AUTO
                && System.console() != null && System.getenv("NO_COLOR") == null
                && !"dumb".equalsIgnoreCase(System.getenv("TERM"));
        out = System.out;
        err = System.err;
    }

    public static CliConsole create(ColorMode mode) {
        return new CliConsole(mode);
    }

    public static CliConsole auto() {
        return create(ColorMode.AUTO);
    }

    public String accent(String text) {
        return style("36", text);
    }

    public String brand(String text) {
        return style("38;2;18;160;215", text);
    }

    public String bold(String text) {
        return style("1", text);
    }

    public void title(String text) {
        out.println(bold(accent(text)));
    }

    public void detail(String text) {
        out.println(style("2", "  " + text));
    }

    public void field(String label, String value) {
        out.println("  " + accent(String.format("%-10s", label)) + " " + value);
    }

    public void step(int number, String text) {
        out.println(style("33", "[" + number + "/2]") + " " + text);
    }

    public void success(String text) {
        out.println(style("32", "[OK]") + " " + text);
    }

    public void error(String text) {
        err.println(style("31;1", "[ERROR]") + " " + text);
    }

    public void hint(String text) {
        err.println(style("2", "  " + text));
    }

    public void blank() {
        out.println();
    }

    private String style(String code, String text) {
        return color ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
    }
}

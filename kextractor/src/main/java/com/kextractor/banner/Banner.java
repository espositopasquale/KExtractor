package com.kextractor.banner;

import com.kextractor.version.Version;

public class Banner {

public static void printBanner() {
    boolean colorSupported = System.console() != null
            && System.getenv("NO_COLOR") == null;

    final String LIGHT_BLUE = colorSupported ? "\u001B[38;2;18;160;215m" : ""; // #12A0D7
    final String BOLD    = colorSupported ? "\u001B[1m" : "";
    final String RESET   = colorSupported ? "\u001B[0m" : "";

    String[] art = {
        " _  __ _____      _                  _            ",
        "| |/ /|  ___|    | |                | |           ",
        "| ' / | |____  __| |_ _ __ __ _  ___| |_ ___  _ __ ",
        "|  <  |  __\\ \\/ /| __| '__/ _` |/ __| __/ _ \\| '__|",
        "| . \\ | |___>  < | |_| | | (_| | (__| || (_) | |   ",
        "|_|\\_\\|____/_/\\_\\ \\__|_|  \\__,_|\\___|\\__\\___/|_|   "
    };

    int width = art[0].length();
    System.out.println();
    for (String line : art) {
        System.out.println(LIGHT_BLUE + line + RESET);
    }
    System.out.println();

    String version = String.format("v%s", Version.VERSION);
    String title = "Semantic Knowledge Extraction CLI";
    String author = "Pasquale Esposito · pasqualeesposito.net";

    printBoxedLine(title + "  " + BOLD + version + RESET, width, LIGHT_BLUE, RESET);
    System.out.println(LIGHT_BLUE + center("created by " + author, width) + RESET);
    System.out.println();
}

private static void printBoxedLine(String text, int width, String borderColor, String reset) {
    String line = "─".repeat(width);
    System.out.println(borderColor + "┌" + line + "┐" + reset);
    System.out.println(borderColor + "│" + reset + center(text, width) + borderColor + "│" + reset);
    System.out.println(borderColor + "└" + line + "┘" + reset);
}

private static String center(String text, int width) {
    int visibleLength = text.replaceAll("\u001B\\[[;\\d]*m", "").length();
    int padding = Math.max(0, width - visibleLength);
    int left = padding / 2;
    int right = padding - left;
    return " ".repeat(left) + text + " ".repeat(right);
}

        public static void printWarmupMessage() {
                System.out.println("\u001B[33m" + "Initializing NLP pipeline..." + "\u001B[0m");
                System.out.println("    Loading CoreNLP models...");
                System.out.println("    This may take 30-90 seconds depending on your hardware.\n");
        }

}

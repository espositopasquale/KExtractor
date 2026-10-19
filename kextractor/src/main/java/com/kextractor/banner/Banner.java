package com.kextractor.banner;

import com.kextractor.app.CliConsole;
import com.kextractor.version.Version;

public final class Banner {

    private Banner() {
    }

    public static void printBanner() {
        printBanner(CliConsole.auto());
    }

    public static void printBanner(CliConsole console) {
        String[] art = {
            " _  __ _____      _                  _            ",
            "| |/ /|  ___|    | |                | |           ",
            "| ' / | |____  __| |_ _ __ __ _  ___| |_ ___  _ __ ",
            "|  <  |  __\\ \\/ /| __| '__/ _` |/ __| __/ _ \\| '__|",
            "| . \\ | |___>  < | |_| | | (_| | (__| || (_) | |   ",
            "|_|\\_\\|____/_/\\_\\ \\__|_|  \\__,_|\\___|\\__\\___/|_|   "
        };
        int width = java.util.Arrays.stream(art).mapToInt(String::length).max().orElse(0);
        String title = "Semantic Knowledge Extraction CLI  v" + Version.VERSION;
        String author = "created by Pasquale Esposito · pasqualeesposito.net";
        width = Math.max(width, Math.max(title.length(), author.length()));
        console.blank();
        for (String line : art) {
            System.out.println(console.brand(line));
        }
        console.blank();
        System.out.println(console.brand("┌" + "─".repeat(width) + "┐"));
        System.out.println(console.brand("│") + console.bold(center(title, width)) + console.brand("│"));
        System.out.println(console.brand("└" + "─".repeat(width) + "┘"));
        System.out.println(console.brand(center(author, width)));
        console.blank();
    }

    private static String center(String text, int width) {
        int padding = Math.max(0, width - text.length());
        return " ".repeat(padding / 2) + text + " ".repeat(padding - padding / 2);
    }

    public static void printWarmupMessage() {
        CliConsole.auto().detail("Loading local NLP models; the first run may take a while.");
    }
}

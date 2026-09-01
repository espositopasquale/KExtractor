package com.kextractor.utils;

public class Utility {

    public static String toLowerCamelCase(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "relatedTo";
        }
        String[] tokens = text.toLowerCase().split("[_\\s]+");
        StringBuilder sb = new StringBuilder(tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            if (!tokens[i].isEmpty()) {
                sb.append(Character.toUpperCase(tokens[i].charAt(0)))
                        .append(tokens[i].substring(1));
            }
        }
        return sb.toString();
    }

}

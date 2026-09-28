package com.kextractor.version;

public final class Version {

    private Version() {}

    public static final String NAME = "KExtractor";
    public static final String VERSION = "0.2.0";

    public static String getVersion() {
        return VERSION;
    }

    public static String getName() {
        return NAME;
    }

    public static String getFullVersion() {
        return NAME + " v" + VERSION;
    }

}
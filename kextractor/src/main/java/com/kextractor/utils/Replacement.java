package com.kextractor.utils;

    public class Replacement {

        public int start, end;
        public String text;

        public Replacement(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
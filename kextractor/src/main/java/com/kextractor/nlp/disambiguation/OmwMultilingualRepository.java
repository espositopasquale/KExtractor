package com.kextractor.nlp.disambiguation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;

public class OmwMultilingualRepository implements AutoCloseable {

    public record Lexicalization(String lemma, String language) {
    }

    private final Connection conn;

    public OmwMultilingualRepository(String sqliteDbPath) throws SQLException {
        java.nio.file.Path path = java.nio.file.Path.of(sqliteDbPath).toAbsolutePath();
        if (!java.nio.file.Files.isRegularFile(path)) {
            throw new SQLException("Missing OMW database: " + path);
        }
        String url = "jdbc:sqlite:" + path.toUri() + "?mode=ro";
        this.conn = DriverManager.getConnection(url);
    }

    public List<String> getTranslations(ISynset synset, String targetLang) throws SQLException {
        return getLexicalizations(synset, targetLang).stream().map(Lexicalization::lemma).distinct().toList();
    }

    public List<Lexicalization> getLexicalizations(ISynset synset, String targetLang) throws SQLException {
        if (synset == null) {
            return List.of();
        }

        String offsetStr = String.format(java.util.Locale.ROOT, "%08d", synset.getOffset());

        String posStr = convertPosToString(synset.getPOS());

        return findLexicalizations(offsetStr, posStr, targetLang);
    }

    public List<String> getLemmas(String synsetOffset, String pos, String targetLang) throws SQLException {
        return findLexicalizations(synsetOffset, pos, targetLang).stream()
                .map(Lexicalization::lemma).distinct().toList();
    }

    private List<Lexicalization> findLexicalizations(String synsetOffset, String pos, String targetLang)
            throws SQLException {
        String language = targetLang.strip().toLowerCase(Locale.ROOT);
        boolean allLanguages = language.equals("all") || language.equals("*");
        List<Lexicalization> labels = new ArrayList<>();
        String sql = "SELECT DISTINCT TRIM(lemma) AS lemma, LOWER(TRIM(lang)) AS lang "
                + "FROM synset_lemmas WHERE synset_offset = ? AND pos = ? "
                + "AND lemma IS NOT NULL AND TRIM(lemma) <> '' "
                + "AND lang IS NOT NULL AND TRIM(lang) <> '' "
                + (allLanguages ? "" : "AND LOWER(TRIM(lang)) = ? ")
                + "ORDER BY lang, lemma";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, synsetOffset);
            pstmt.setString(2, pos.toLowerCase(java.util.Locale.ROOT));
            if (!allLanguages) {
                pstmt.setString(3, language);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    labels.add(new Lexicalization(rs.getString("lemma"), rs.getString("lang")));
                }
            }
        }
        return labels;
    }

    private String convertPosToString(POS pos) {
        if (pos == null) {
            return "n";
        }
        switch (pos) {
            case NOUN:
                return "n";
            case VERB:
                return "v";
            case ADJECTIVE:
                return "a";
            case ADVERB:
                return "r";
            default:
                return pos.getTag() + "";
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

}

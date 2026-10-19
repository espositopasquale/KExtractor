package com.kextractor.nlp.disambiguation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;

public class OmwMultilingualRepository implements AutoCloseable {

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
        if (synset == null) {
            return List.of();
        }

        String offsetStr = String.format(java.util.Locale.ROOT, "%08d", synset.getOffset());

        String posStr = convertPosToString(synset.getPOS());

        return getLemmas(offsetStr, posStr, targetLang);
    }

    public List<String> getLemmas(String synsetOffset, String pos, String targetLang) throws SQLException {
        List<String> lemmas = new ArrayList<>();

        String sql = "SELECT DISTINCT lemma FROM synset_lemmas WHERE synset_offset = ? AND pos = ? AND lang = ? ORDER BY lemma";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, synsetOffset);
            pstmt.setString(2, pos.toLowerCase(java.util.Locale.ROOT));
            pstmt.setString(3, targetLang.toLowerCase(java.util.Locale.ROOT));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    lemmas.add(rs.getString("lemma"));
                }
            }
        }
        return lemmas;
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

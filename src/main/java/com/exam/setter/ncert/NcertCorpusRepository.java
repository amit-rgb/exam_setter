package com.exam.setter.ncert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class NcertCorpusRepository {
    private final JdbcTemplate jdbc;

    public NcertCorpusRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<State> find(String documentKey) {
        return jdbc.query("SELECT document_key, content_hash, status, chunk_version, chunk_count FROM ncert_corpus_document WHERE document_key = ?",
                rs -> rs.next() ? Optional.of(new State(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5))) : Optional.empty(), documentKey);
    }

    public void start(NcertCorpusManifest.Entry e, String corpusVersion, String hash, String chunkVersion) {
        jdbc.update("""
                INSERT INTO ncert_corpus_document
                (document_key, corpus_version, language, class_level, subject, book_code, book_title,
                 chapter_number, chapter_title, file_name, source_url, content_hash, chunk_version,
                 chunk_count, status, error_message, started_at, completed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'RUNNING', NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                ON CONFLICT (document_key) DO UPDATE SET
                  corpus_version=EXCLUDED.corpus_version, language=EXCLUDED.language, class_level=EXCLUDED.class_level,
                  subject=EXCLUDED.subject, book_code=EXCLUDED.book_code, book_title=EXCLUDED.book_title,
                  chapter_number=EXCLUDED.chapter_number, chapter_title=EXCLUDED.chapter_title, file_name=EXCLUDED.file_name,
                  source_url=EXCLUDED.source_url, content_hash=EXCLUDED.content_hash, chunk_version=EXCLUDED.chunk_version,
                  chunk_count=0, status='RUNNING', error_message=NULL, started_at=CURRENT_TIMESTAMP,
                  completed_at=NULL, updated_at=CURRENT_TIMESTAMP
                """, e.documentKey(), corpusVersion, e.language(), e.classLevel(), e.subject(), e.bookCode(), e.bookTitle(),
                e.chapterNumber(), e.chapterTitle(), e.fileName(), e.sourceUrl(), hash, chunkVersion);
    }

    public void complete(String key, int chunks) {
        jdbc.update("UPDATE ncert_corpus_document SET chunk_count=?, status='COMPLETED', error_message=NULL, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE document_key=?", chunks, key);
    }

    public void fail(String key, String message) {
        jdbc.update("UPDATE ncert_corpus_document SET status='FAILED', error_message=?, updated_at=CURRENT_TIMESTAMP WHERE document_key=?", truncate(message), key);
    }

    private String truncate(String message) {
        if (message == null) return "Unknown ingestion failure";
        return message.length() <= 4000 ? message : message.substring(0, 4000);
    }

    public record State(String documentKey, String contentHash, String status, String chunkVersion, int chunkCount) {}
}

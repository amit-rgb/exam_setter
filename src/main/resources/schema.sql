CREATE TABLE IF NOT EXISTS ncert_corpus_document (
    id BIGSERIAL PRIMARY KEY,
    document_key VARCHAR(300) NOT NULL UNIQUE,
    corpus_version VARCHAR(50) NOT NULL,
    language VARCHAR(50) NOT NULL,
    class_level VARCHAR(50) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    book_code VARCHAR(100) NOT NULL,
    book_title VARCHAR(300),
    chapter_number INTEGER,
    chapter_title VARCHAR(500),
    file_name VARCHAR(300) NOT NULL,
    source_url TEXT,
    content_hash CHAR(64) NOT NULL,
    chunk_version VARCHAR(50) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ncert_corpus_document_status
    ON ncert_corpus_document(status);
CREATE INDEX IF NOT EXISTS idx_ncert_corpus_document_book
    ON ncert_corpus_document(corpus_version, book_code, chapter_number);
CREATE INDEX IF NOT EXISTS idx_ncert_corpus_document_hash
    ON ncert_corpus_document(content_hash);

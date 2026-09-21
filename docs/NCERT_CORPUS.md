# Production-grade NCERT corpus ingestion

This subsystem is intentionally separate from the public `/api/ingest/pdf` endpoint. The public endpoint remains useful for small, user-supplied source documents; the NCERT corpus is loaded from a controlled local corpus produced by the NCERT downloader.

## Pipeline

`NCERT official source -> downloader -> PDFs + manifest.json -> checksum validation -> page-aware extraction -> token chunking -> deterministic vector IDs -> pgvector`

The manifest is the source of truth. One entry should represent one logical retrieval document, normally one chapter PDF. This avoids indexing both a complete book and its chapter PDFs and thereby duplicating the same content.

## Required manifest shape

```json
{
  "schemaVersion": "1",
  "corpusVersion": "2026",
  "entries": [
    {
      "documentKey": "NCERT-CLASS11-CHEMISTRY-KECH1-EN-CH06",
      "language": "ENGLISH",
      "classLevel": "CLASS_11",
      "subject": "CHEMISTRY",
      "bookCode": "kech1",
      "bookTitle": "Chemistry Part I",
      "chapterNumber": 6,
      "chapterTitle": "Thermodynamics",
      "fileName": "CLASS_11/CHEMISTRY/kech106.pdf",
      "sourceUrl": "https://ncert.nic.in/textbook.php?kech1=1-7",
      "targetLevels": ["CLASS_11"],
      "contentScope": "CHAPTER",
      "contentHash": "<sha256-of-pdf>"
    }
  ]
}
```

`contentHash` is optional in the manifest, but strongly recommended. The ingestion service always calculates the SHA-256 itself and stores the actual hash.

## Idempotency and recovery

- The logical `documentKey` is unique in PostgreSQL.
- An unchanged completed document is skipped.
- A changed document is re-indexed.
- Previously generated deterministic chunk IDs are deleted before replacement.
- Each document has durable `RUNNING`, `COMPLETED`, or `FAILED` state.
- A failed document does not prevent the rest of the corpus from being processed unless `NCERT_INGESTION_FAIL_FAST=true`.
- The corpus and chunk versions are explicit so a new chunking strategy does not silently mix incompatible vector representations.

## Run

Keep the application default disabled. For a dedicated corpus-loading process, set:

```text
NCERT_INGESTION_ENABLED=true
NCERT_CORPUS_ROOT=D:/path/to/NCERT
NCERT_CORPUS_MANIFEST=D:/path/to/NCERT/manifest.json
NCERT_CORPUS_VERSION=2026
NCERT_CHUNK_VERSION=v1
```

Then start the Spring Boot application once. The runner processes the manifest and exits with a failure status if any entries failed.

For production, run this as a separate deployment/job rather than enabling it on every normal web-server startup.

## Retrieval metadata

Each vector carries `source=NCERT`, corpus/chunk versions, language, class, subject, target levels, book/chapter identity, source URL, PDF hash, chunk index, and page number. This makes it possible to add strict NCERT-only filters and citation-aware retrieval later without rebuilding the corpus.

## Source policy

The downloader should obtain books from NCERT's official textbook portal and retain the official source URL in the manifest. Do not treat third-party textbook mirrors as canonical sources.

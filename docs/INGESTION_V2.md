# Production Ingestion v2

The ingestion pipeline now uses structure-aware chunking, document hashing, lifecycle tracking, hybrid retrieval and contextual expansion.

## Chunking

- Target chunk size: 2400 characters for user uploads.
- Overlap: 400 characters.
- Chunks prefer paragraph and heading boundaries.
- Heading metadata is preserved as `sectionTitle`.
- Table-like blocks are marked with `contentScope=TABLE`.
- Chunk metadata includes `chunkIndex`, `chunkVersion`, `pipelineVersion`, `documentKey` and `contentHash`.

NCERT keeps page boundaries and uses the same structural chunker with the existing NCERT chunk-size settings.

## Retrieval

Retrieval combines:

1. pgvector semantic similarity
2. PostgreSQL full-text search
3. score merging
4. neighboring chunk expansion
5. existing source/class/book/chapter reranking

The OpenAI embedding configuration uses metadata-aware embedding mode so structural metadata contributes context to the embedding.

## Deduplication and versioning

Documents are hashed with SHA-256. A completed document with the same content hash and chunk version is skipped.

Changing the chunk version causes the document to be indexed again. Pipeline and chunk versions are stored in metadata and in the ingestion dashboard.

## Lifecycle

Documents move through:

`PARSING → CHUNKING → INDEXING → COMPLETED`

Failures are recorded as `FAILED` with an error message.

The asynchronous endpoint is:

`POST /api/ingest/pdf/async`

The existing synchronous endpoint remains available:

`POST /api/ingest/pdf`

## Dashboard

The ingestion dashboard exposes:

- indexed document count
- chunk count
- completed/failed counts
- lifecycle status
- pages/chunks
- pipeline/chunk versions
- document metadata
- indexed source content

## Re-indexing

For a clean NCERT re-index after changing the chunk version, run the existing NCERT ingestion profile. The ingestion service removes the previous deterministic NCERT chunks before indexing the new version.

For user uploads, re-uploading the same document with the same metadata and chunk version is skipped. Changing the chunk version forces a new index.

## Scanned PDFs

The pipeline detects PDFs with no extractable text and reports that OCR is required. Native OCR is intentionally not enabled by default because it requires an external OCR runtime (for example Tesseract) and should be enabled deliberately in the deployment image.

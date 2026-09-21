# Assessment Studio: Complete Exam-Aware NCERT Workflow

## Architecture

```text
Official NCERT PDFs + manifest
        |
        v
NCERT production ingestion
  SHA-256 -> PDFBox -> chunking -> deterministic IDs
        |
        v
PostgreSQL / pgvector
        |
        +--> strict NCERT retrieval
        |      source + corpus + class + subject + book + chapter
        |      semantic search + metadata-aware reranking
        |
        +--> PYQ retrieval (pattern evidence only)
        |
        v
Exam-aware question generation
        |
        v
Review / moderation / selection
        |
        v
PDF export
```

## NCERT corpus

The canonical source is the official NCERT textbook download. Keep chapter PDFs as the retrieval units; do not index both complete-book and chapter PDFs for the same corpus version because that duplicates evidence.

Required manifest fields include `documentKey`, `language`, `classLevel`, `subject`, `bookCode`, `bookTitle`, `chapterNumber`, `chapterTitle`, `fileName`, `sourceUrl`, `targetLevels`, and optional `contentHash`.

## Bulk ingestion

Normal web deployment does not ingest the corpus on startup.

Run the dedicated Compose profile after the local `NCERT/manifest.json` and PDFs are present:

```powershell
docker compose --profile ncert up --build --abort-on-container-exit ncert-ingest
```

The runner exits non-zero when entries fail. The ingestion job is idempotent: unchanged documents are skipped; changed documents are reindexed with deterministic chunk IDs; failed replacement jobs clean up partial chunks.

## Admin endpoints

- `GET /api/admin/ncert/corpus/status`
- `GET /api/admin/ncert/status`
- `GET /api/admin/ncert/catalog/summary`
- `GET /api/admin/ncert/catalog/books?subject=CHEMISTRY&targetLevel=CLASS_11`
- `GET /api/admin/ncert/catalog/chapters?bookCode=kech1&targetLevel=CLASS_11`
- `GET /api/admin/ncert/retrieval/diagnostic?subject=CHEMISTRY&targetLevels=CLASS_11&query=thermodynamics`
- `GET /api/admin/ncert/corpus/job`
- `POST /api/admin/ncert/corpus/ingest` (disabled unless `NCERT_ADMIN_INGESTION_ENABLED=true`)

The browser control center is `admin.html`.

## Exam profiles

Exam profiles are persisted through `/api/exam-profiles` and can define:

- exam ID/name and subject
- target levels
- `knowledgeSource`: `NCERT`, `MIXED`, or `USER_UPLOAD`
- NCERT corpus version
- optional NCERT book/chapter scope
- question count, marks and duration
- difficulty/type/topic distributions
- instructions and previous-year range

Blueprint generation using `knowledgeSource=NCERT` requires an exam profile. This prevents an accidental fallback to broad user-upload retrieval.

## Retrieval policy

NCERT retrieval always filters on:

- `source == NCERT`
- `sourceType == TEXTBOOK`
- `corpusVersion`
- `subject`
- `classLevel` / target level
- optional `bookCode`
- optional `chapterNumber`

Candidate retrieval uses semantic similarity and then reranks with exact book/chapter/class matches and per-document diversity. PYQs are retrieved separately and are supplied only as pattern evidence.

## Frontend workflow

1. Ingest user source material when needed.
2. Open Blueprint and select an exam profile.
3. Select NCERT source policy and optional book/chapter scope.
4. Define section question type/count/marks/difficulty/topic.
5. Generate the paper.
6. Review, opt questions in/out, approve or reject questions, and inspect retrieval sources.
7. Export the selected paper.

The administration page is `admin.html`.

## Production safety

- Keep NCERT ingestion disabled in the normal public web service.
- Use the `ncert` Compose profile for bulk ingestion.
- Keep `NCERT_ADMIN_INGESTION_ENABLED=false` unless interactive ingestion is explicitly required.
- Keep Basic Auth enabled for the application and admin endpoints.
- Do not commit `.env`, API keys, database passwords, PDFs, or the generated NCERT corpus.

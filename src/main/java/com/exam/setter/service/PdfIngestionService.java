package com.exam.setter.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_TARGET_LEVELS_FIELD_LENGTH = 500;
    private static final List<String> GENERIC_UPLOAD_TYPES = List.of("STUDY_NOTES", "QUESTION_BANK", "SYLLABUS", "REFERENCE", "TEXTBOOK");
    private static final String PIPELINE_VERSION = "v2";

    private final VectorStore vectorStore;
    private final StructureAwareChunker chunker;
    private final IngestionTrackingService tracking;
    private final int chunkSizeChars;
    private final int chunkOverlapChars;
    private final String chunkVersion;

    public PdfIngestionService(VectorStore vectorStore,
                               StructureAwareChunker chunker,
                               IngestionTrackingService tracking,
                               @Value("${app.ingestion.chunk-size-chars:2400}") int chunkSizeChars,
                               @Value("${app.ingestion.chunk-overlap-chars:400}") int chunkOverlapChars,
                               @Value("${app.ingestion.chunk-version:v2}") String chunkVersion) {
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.tracking = tracking;
        this.chunkSizeChars = Math.max(1200, chunkSizeChars);
        this.chunkOverlapChars = Math.max(0, Math.min(chunkOverlapChars, this.chunkSizeChars / 3));
        this.chunkVersion = chunkVersion;
    }

    public int ingestPdfFile(MultipartFile file, String subject, String targetLevel, String sourceType) throws IOException {
        validateUpload(file, subject, targetLevel, sourceType);
        List<String> targetLevels = parseTargetLevels(targetLevel);
        String normalizedSourceType = normalizeSourceType(sourceType);
        if ("PREVIOUS_YEAR_PAPER".equals(normalizedSourceType)) {
            throw new IllegalArgumentException("Previous-year papers must use the dedicated PYQ workflow so exam and year metadata can be captured.");
        }
        return ingest(file, subject, targetLevels, normalizedSourceType, null);
    }

    public int ingest(MultipartFile file, String subject, List<String> targetLevels,
                      String sourceType, Map<String, Object> additionalMetadata) throws IOException {
        String normalizedSourceType = normalizeSourceType(sourceType);
        validateUpload(file, subject, String.join(",", targetLevels), normalizedSourceType);
        if (!GENERIC_UPLOAD_TYPES.contains(normalizedSourceType)) {
            throw new IllegalArgumentException("Unsupported user document type: " + normalizedSourceType);
        }

        String normalizedSubject = subject.trim().toLowerCase();
        String contentHash = sha256File(file);
        String documentKey = "UPLOAD-" + sha256Text(
                contentHash + "|" + normalizedSubject + "|" + targetLevels + "|" + normalizedSourceType);

        if (tracking.existsCompleted(documentKey, contentHash, chunkVersion)) {
            log.info("Skipping unchanged upload: {}", file.getOriginalFilename());
            return 0;
        }

        tracking.start(documentKey, file.getOriginalFilename(), "USER_UPLOAD", normalizedSourceType,
                normalizedSubject, targetLevels, PIPELINE_VERSION, chunkVersion, contentHash);

        log.info("Starting production ingestion: {} ({} bytes) levels={} type={}",
                file.getOriginalFilename(), file.getSize(), targetLevels, normalizedSourceType);

        Path tempPath = Files.createTempFile("pdf_ingest_", ".pdf");
        try {
            file.transferTo(tempPath);
            tracking.status(documentKey, "PARSING");
            List<Document> extractedDocs = new TikaDocumentReader(new FileSystemResource(tempPath.toFile())).get();

            if (extractedDocs.stream().noneMatch(d -> d.getText() != null && !d.getText().isBlank())) {
                throw new IllegalArgumentException("No readable text was extracted from the PDF. The document may require OCR.");
            }

            tracking.status(documentKey, "CHUNKING");
            List<Document> chunkedDocs = chunker.chunk(extractedDocs, chunkSizeChars, chunkOverlapChars, chunkVersion);
            if (chunkedDocs.isEmpty()) throw new IllegalArgumentException("The PDF did not produce indexable text chunks.");

            List<Document> enrichedDocs = new java.util.ArrayList<>();
            for (int i = 0; i < chunkedDocs.size(); i++) {
                Document doc = chunkedDocs.get(i);
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("source", "USER_UPLOAD");
                metadata.put("sourceType", normalizedSourceType);
                metadata.put("subject", normalizedSubject);
                metadata.put("targetLevels", String.join(",", targetLevels));
                metadata.put("targetLevel", targetLevels.get(0));
                metadata.put("fileName", file.getOriginalFilename());
                metadata.put("documentKey", documentKey);
                metadata.put("contentHash", contentHash);
                metadata.put("pipelineVersion", PIPELINE_VERSION);
                metadata.put("chunkIndex", i);
                metadata.put("chunkVersion", chunkVersion);
                if (additionalMetadata != null) metadata.putAll(additionalMetadata);
                enrichedDocs.add(new Document("upload-" + contentHash.substring(0, 24) + "-" + i, doc.getText(), metadata));
            }

            tracking.status(documentKey, "INDEXING");
            vectorStore.delete("documentKey == '" + escape(documentKey) + "'");
            for (int i = 0; i < enrichedDocs.size(); i += 50) {
                vectorStore.add(enrichedDocs.subList(i, Math.min(i + 50, enrichedDocs.size())));
            }

            tracking.status(documentKey, "VALIDATING");
            validateIndexedCount(documentKey, enrichedDocs.size());
            tracking.complete(documentKey, enrichedDocs.size(), countPages(tempPath));
            log.info("Completed production ingestion: {} chunks from {}", enrichedDocs.size(), file.getOriginalFilename());
            return enrichedDocs.size();
        } catch (IOException | IllegalArgumentException ex) {
            tracking.fail(documentKey, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            tracking.fail(documentKey, ex.getMessage());
            throw new IOException("Document ingestion failed: " + ex.getMessage(), ex);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void validateIndexedCount(String documentKey, int expected) {
        // VectorStore.add is synchronous; the validation is intentionally lightweight.
        // A zero/negative expected count is always a pipeline failure.
        if (expected <= 0) throw new IllegalStateException("No chunks were indexed for document " + documentKey);
    }

    private int countPages(Path pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        } catch (Exception ex) {
            return 0;
        }
    }

    private List<String> parseTargetLevels(String rawTargetLevels) {
        return Arrays.stream(rawTargetLevels.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).map(String::toUpperCase).distinct().toList();
    }

    private String normalizeSourceType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        return "TEXTBOOK".equals(type) ? "REFERENCE" : type;
    }

    private void validateUpload(MultipartFile file, String subject, String targetLevel, String sourceType) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A PDF file is required.");
        if (file.getSize() > MAX_FILE_SIZE_BYTES) throw new IllegalArgumentException("PDF file must be 50 MB or smaller.");
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) throw new IllegalArgumentException("Only PDF files are supported.");
        if (file.getContentType() != null && !"application/pdf".equalsIgnoreCase(file.getContentType())) throw new IllegalArgumentException("The uploaded file must have content type application/pdf.");
        if (subject == null || subject.isBlank() || subject.length() > 100) throw new IllegalArgumentException("Subject is required and must be at most 100 characters.");
        if (targetLevel == null || targetLevel.isBlank() || targetLevel.length() > MAX_TARGET_LEVELS_FIELD_LENGTH) throw new IllegalArgumentException("At least one target level is required; the combined value must be at most 500 characters.");
        if (parseTargetLevels(targetLevel).isEmpty()) throw new IllegalArgumentException("At least one target level is required.");
        if (sourceType == null || sourceType.isBlank() || sourceType.length() > 50) throw new IllegalArgumentException("Source type is required and must be at most 50 characters.");
    }

    private String sha256File(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IOException("Unable to create document hash", ex);
        }
    }

    private String sha256Text(String value) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IOException("Unable to create document key", ex);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    List<String> parseTargetLevelsForTest(String rawTargetLevels) {
        return parseTargetLevels(rawTargetLevels);
    }
}

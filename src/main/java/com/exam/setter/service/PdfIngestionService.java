package com.exam.setter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class PdfIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_TARGET_LEVELS_FIELD_LENGTH = 500;
    private final VectorStore vectorStore;

    public PdfIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Ingests one source PDF and associates the same embedded chunks with one or
     * more target levels. The existing targetLevel request parameter remains
     * compatible: a single value is still accepted, while comma-separated values
     * are now supported (for example CLASS_11,CLASS_12,UPSC).
     */
    public int ingestPdfFile(MultipartFile file, String subject, String targetLevel, String sourceType) throws IOException {
        validateUpload(file, subject, targetLevel, sourceType);
        List<String> targetLevels = parseTargetLevels(targetLevel);
        log.info("Starting ingestion for: {} ({} bytes) for levels {}", file.getOriginalFilename(), file.getSize(), targetLevels);

        Path tempPath = Files.createTempFile("pdf_ingest_", ".pdf");
        file.transferTo(tempPath);

        List<Document> extractedDocs;
        try {
            TikaDocumentReader documentReader = new TikaDocumentReader(new FileSystemResource(tempPath.toFile()));
            extractedDocs = documentReader.get();
        } finally {
            Files.deleteIfExists(tempPath);
        }

        TokenTextSplitter splitter = new TokenTextSplitter(500, 80, 10, 5000, true);
        List<Document> chunkedDocs = splitter.apply(extractedDocs);

        List<Document> enrichedDocs = chunkedDocs.stream().map(doc -> {
            Map<String, Object> metadata = doc.getMetadata();
            String normalizedSubject = subject.trim().toLowerCase();
            metadata.put("subject", normalizedSubject);
            // New canonical representation: one source/chunk can belong to many levels.
            metadata.put("targetLevels", targetLevels);
            // Keep the legacy scalar field so existing data/consumers remain compatible.
            metadata.put("targetLevel", targetLevels.get(0));
            metadata.put("sourceType", sourceType.trim().toUpperCase());
            metadata.put("fileName", file.getOriginalFilename());
            return new Document(doc.getText(), metadata);
        }).toList();

        int batchSize = 50;
        for (int i = 0; i < enrichedDocs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, enrichedDocs.size());
            vectorStore.add(enrichedDocs.subList(i, end));
            log.info("Indexed chunk batch: {} to {}", i + 1, end);
        }

        log.info("Successfully ingested {} chunks from {} for levels {}", enrichedDocs.size(), file.getOriginalFilename(), targetLevels);
        return enrichedDocs.size();
    }

    private List<String> parseTargetLevels(String rawTargetLevels) {
        return Arrays.stream(rawTargetLevels.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private void validateUpload(MultipartFile file, String subject, String targetLevel, String sourceType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("PDF file must be 50 MB or smaller.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }
        if (file.getContentType() != null && !"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("The uploaded file must have content type application/pdf.");
        }
        if (subject == null || subject.isBlank() || subject.length() > 100) {
            throw new IllegalArgumentException("Subject is required and must be at most 100 characters.");
        }
        if (targetLevel == null || targetLevel.isBlank() || targetLevel.length() > MAX_TARGET_LEVELS_FIELD_LENGTH) {
            throw new IllegalArgumentException("At least one target level is required; the combined value must be at most 500 characters.");
        }
        if (parseTargetLevels(targetLevel).isEmpty()) {
            throw new IllegalArgumentException("At least one target level is required.");
        }
        if (sourceType == null || sourceType.isBlank() || sourceType.length() > 50) {
            throw new IllegalArgumentException("Source type is required and must be at most 50 characters.");
        }
    }
}
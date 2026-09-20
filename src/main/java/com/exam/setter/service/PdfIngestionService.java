package com.exam.setter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public PdfIngestionService(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compatible ingestion entry point. The caller must identify the
     * document type via sourceType; the default remains TEXTBOOK for legacy clients.
     */
    public int ingestPdfFile(MultipartFile file, String subject, String targetLevel, String sourceType) throws IOException {
        validateUpload(file, subject, targetLevel, sourceType);
        List<String> targetLevels = parseTargetLevels(targetLevel);
        String normalizedSourceType = sourceType.trim().toUpperCase();
        if ("PREVIOUS_YEAR_PAPER".equals(normalizedSourceType)) {
            throw new IllegalArgumentException("Previous-year papers must be ingested through the PYQ workflow so exam and year metadata can be captured.");
        }
        return ingest(file, subject, targetLevels, normalizedSourceType, null);
    }

    public int ingest(MultipartFile file, String subject, List<String> targetLevels,
                      String sourceType, Map<String, Object> additionalMetadata) throws IOException {
        validateUpload(file, subject, String.join(",", targetLevels), sourceType);
        String normalizedSubject = subject.trim().toLowerCase();
        String normalizedSourceType = sourceType.trim().toUpperCase();
        log.info("Starting ingestion for: {} ({} bytes) for levels {} as {}", file.getOriginalFilename(), file.getSize(), targetLevels, normalizedSourceType);

        Path tempPath = Files.createTempFile("pdf_ingest_", ".pdf");
        file.transferTo(tempPath);

        List<Document> extractedDocs;
        try {
            extractedDocs = new TikaDocumentReader(new FileSystemResource(tempPath.toFile())).get();
        } finally {
            Files.deleteIfExists(tempPath);
        }

        String documentKey = "UPLOAD-" + sha256Hex((file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
                + "|" + normalizedSubject + "|" + targetLevels + "|" + normalizedSourceType + "|" + file.getSize());
        TokenTextSplitter splitter = new TokenTextSplitter(500, 80, 10, 5000, true);
        List<Document> chunkedDocs = splitter.apply(extractedDocs);
        List<Document> enrichedDocs = chunkedDocs.stream().map(doc -> {
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("source", "USER_UPLOAD");
            metadata.put("sourceType", normalizedSourceType);
            metadata.put("subject", normalizedSubject);
            metadata.put("targetLevels", String.join(",", targetLevels));
            metadata.put("targetLevel", targetLevels.get(0));
            metadata.put("fileName", file.getOriginalFilename());
            metadata.put("documentKey", documentKey);
            if (additionalMetadata != null) metadata.putAll(additionalMetadata);
            return new Document(doc.getText(), metadata);
        }).toList();

        for (int i = 0; i < enrichedDocs.size(); i += 50) {
            int end = Math.min(i + 50, enrichedDocs.size());
            vectorStore.add(enrichedDocs.subList(i, end));
            log.info("Indexed chunk batch: {} to {}", i + 1, end);
        }
        log.info("Successfully ingested {} chunks from {}", enrichedDocs.size(), file.getOriginalFilename());
        return enrichedDocs.size();
    }

    private List<String> parseTargetLevels(String rawTargetLevels) {
        return Arrays.stream(rawTargetLevels.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(String::toUpperCase).distinct().toList();
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

    private String sha256Hex(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IOException("Unable to create document key", ex);
        }
    }

    List<String> parseTargetLevelsForTest(String rawTargetLevels) {
        return parseTargetLevels(rawTargetLevels);
    }
}
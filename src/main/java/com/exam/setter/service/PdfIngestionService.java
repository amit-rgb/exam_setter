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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class PdfIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);
    private final VectorStore vectorStore;

    public PdfIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingestPdfFile(MultipartFile file, String subject, String targetLevel, String sourceType) throws IOException {
        log.info("Starting ingestion for: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        // Write to temp file to avoid byte array stream cache mismatch in PDFBox
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
            metadata.put("subject", subject.trim().toLowerCase());
            metadata.put("targetLevel", targetLevel.trim().toUpperCase());
            metadata.put("sourceType", sourceType.trim().toUpperCase());
            metadata.put("fileName", file.getOriginalFilename());
            return new Document(doc.getText(), metadata);
        }).toList();

        // Batch upload in blocks of 50 to avoid OpenAI payload limits
        int batchSize = 50;
        for (int i = 0; i < enrichedDocs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, enrichedDocs.size());
            vectorStore.add(enrichedDocs.subList(i, end));
            log.info("Indexed chunk batch: {} to {}", i + 1, end);
        }

        log.info("Successfully ingested {} chunks from {}", enrichedDocs.size(), file.getOriginalFilename());
        return enrichedDocs.size();
    }
}
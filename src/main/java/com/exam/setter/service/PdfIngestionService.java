package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class PdfIngestionService {

    private final VectorStore vectorStore;

    public PdfIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingestPdfFile(MultipartFile file, String subject, String targetLevel, String sourceType) throws IOException {
        Resource resource = new InputStreamResource(file.getInputStream());
        TikaDocumentReader documentReader = new TikaDocumentReader(resource);
        List<Document> extractedDocs = documentReader.get();

        TokenTextSplitter splitter = new TokenTextSplitter(500, 80, 10, 5000, true);
        List<Document> chunkedDocs = splitter.apply(extractedDocs);

        List<Document> enrichedDocs = chunkedDocs.stream().map(doc -> {
            Map<String, Object> metadata = doc.getMetadata();
            metadata.put("subject", subject.trim().toLowerCase());
            metadata.put("targetLevel", targetLevel.trim().toUpperCase()); // e.g., "CLASS_11", "CLASS_12", "NEET"
            metadata.put("sourceType", sourceType.trim().toUpperCase());
            metadata.put("fileName", file.getOriginalFilename());
            return new Document(doc.getText(), metadata);
        }).toList();

        vectorStore.add(enrichedDocs);
        return enrichedDocs.size();
    }
}